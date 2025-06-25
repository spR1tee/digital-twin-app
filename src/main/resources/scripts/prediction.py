import json
import sqlite3
import sys
import os

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from error_metrics import ErrorMetrics
from sklearn.linear_model import LinearRegression
from predictor_model import PredictorModel
from statsmodels.tsa.arima.model import ARIMA
from pmdarima import auto_arima
from prophet import Prophet
from sklearn.preprocessing import MinMaxScaler
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense


class ArimaModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("ARIMA", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        if self._simulation_settings["predictor"]["hyperparameters"].get("auto_optimize", False):
                model = auto_arima(
                    dataframe["feature"].values,
                    start_p=1, start_q=1,
                    max_p=15, max_q=15,
                    seasonal=False,
                    stepwise=True,
                    suppress_warnings=True
                )
                result = model.predict(n_periods=prediction_length)
        else:
            model = ARIMA(
                dataframe["feature"].values,
                order=(
                    self._simulation_settings["predictor"]["hyperparameters"]["p_value"],
                    self._simulation_settings["predictor"]["hyperparameters"]["d_value"],
                    self._simulation_settings["predictor"]["hyperparameters"]["q_value"],
                )
            )
            fitted = model.fit()
            result = fitted.forecast(
                prediction_length
            )

        return result.tolist()

class LinearRegressionModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("LINEAR_REGRESSION", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = LinearRegression()
        model.fit(np.array(dataframe["timestamp"].values).reshape(-1, 1), dataframe["feature"].values)

        future_timestamps = PredictorModel.create_future_timestamp(dataframe=dataframe,
                                                                   prediction_length=prediction_length)
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist()



def db_connect():
    # Bemeneti paraméterek: feature, history length, predikciós hossz, VM szám, tenant azonosító
    feature = sys.argv[1]
    based_on = int(sys.argv[2])
    pred_length = int(sys.argv[3])
    vm_count = int(sys.argv[4])
    tenant_id = sys.argv[5]

    # SQLite adatbázis elérési út
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_path = os.path.join(script_dir, "..", "spring_db", f"{tenant_id}.db")
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # SQL lekérdezés a szükséges adatokhoz (timestamp, érték, VM név)
    query = f"""
    SELECT rd.timestamp, vd.{feature}, vd.name
    FROM request_data rd
    LEFT JOIN vm_data vd ON rd.id = vd.request_data_id
    ORDER BY rd.timestamp DESC
    LIMIT {based_on * vm_count};
    """

    cursor.execute(query)
    rows = cursor.fetchall()
    conn.close()

    return rows, based_on, pred_length, vm_count

def error_metrics(data):
    index = int(len(data) * 3 / 4)  # 75%-os tanító és 25%-os teszt szétválasztá
    actual = [tuple[1] for tuple in data[index:]]
    train_data = data[:index]

    df = pd.DataFrame(train_data, columns=["timestamp", "feature"])
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    start_time = df["timestamp"].min()
    df["timestamp"] = (df["timestamp"] - start_time).dt.total_seconds()

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "feature_for_metrics"
    prediction_length = len(actual) * 5
    is_test_data = False

    predictions = linear_regression_model.predict(feature_names, df, prediction_length, is_test_data)

    print("MSE:")
    print(ErrorMetrics.MSE(actual, predictions))

    print("RMSE:")
    print(ErrorMetrics.RMSE(actual, predictions))

    print("MAE:")
    print(ErrorMetrics.MAE(actual, predictions))


def do_pred():
    rows, based_on, pred_length, vm_count = db_connect()
    paired_data_lists = {}
    linear_regression_predictions_list = {}
    arima_predictions_list = {}
    random_forest_pred_list = {}


    # Inicializálás külön VM-ekre
    for i in range(vm_count):
        paired_data_lists[f"paired_data{i if i > 0 else ''}"] = []
        linear_regression_predictions_list[f"prediction{i if i > 0 else ''}"] = []
        arima_predictions_list[f"prediction{i if i > 0 else ''}"] = []
        random_forest_pred_list[f"prediction{i if i > 0 else ''}"] = []

    rows.reverse()  # Időrendbe helyezés

    # Adatok felosztása VM-ek szerint
    if len(rows) >= based_on * vm_count:
        for i in range(0, based_on * vm_count, vm_count):
            for j in range(vm_count):
                value = rows[i + j][1]
                timestamp = rows[i + j][0]
                list_name = f"paired_data{j if j > 0 else ''}"
                paired_data_lists[list_name].append((timestamp, value))
    else:
        print("Not enough data")
        sys.exit(1)

    # Hibametrikák számítása minden VM-re
    for list_name in paired_data_lists.keys():
        error_metrics(paired_data_lists[list_name])

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "feature"
    prediction_length = pred_length
    is_test_data = False

    simulation_settings_arima = {
        "predictor": {
            "hyperparameters": {
                "p_value": 4,
                "d_value": 0,
                "q_value": 1,
                "auto_optimize": False
            }
        }
    }
    # arima_model = ArimaModel(simulation_settings_arima)

    # Predikciók VM-enként
    for i in range(vm_count):
        list_name = f"paired_data{i if i > 0 else ''}"
        data_list = paired_data_lists[list_name]
        df = pd.DataFrame(data_list, columns=["timestamp", "feature"])
        values_list = df["feature"].tolist()
        df["timestamp"] = pd.to_datetime(df["timestamp"])
        start_time = df["timestamp"].min()
        df["timestamp"] = (df["timestamp"] - start_time).dt.total_seconds()
        pred_name = f"prediction{i if i > 0 else ''}"
        linear_regression_predictions_list[pred_name] = linear_regression_model.predict(feature_names,
                                                                      df,
                                                                      prediction_length,
                                                                      is_test_data)
        # tmp = arima_model.predict(feature_names, df, prediction_length, is_test_data)
        # compressed = compression(tmp)
        # arima_predictions_list[pred_name] = compressed
        # random_forest_pred_list[pred_name] = random_forest(values_list, pred_name, prediction_length)

    for i in range(vm_count):
        list_name = f"prediction{i if i > 0 else ''}"
        data_list = linear_regression_predictions_list[list_name]
        # random_forest_list = random_forest_pred_list[list_name]
        print(f"Linear Regression predictions for vm{i}:\n{data_list}")
        # print(f"Random Forest predictions for vm{i}:\n{random_forest_list}")

    print("JSON_DATA_START")
    result_data_lr = {}
    result_data_arima = {}
    for i in range(vm_count):
        list_name = f"prediction{i if i > 0 else ''}"
        data_list = linear_regression_predictions_list[list_name]
        # rf_list = random_forest_pred_list[list_name]
        # arima_list = arima_predictions_list[list_name]
        result_data_lr[f"VM{i}"] = data_list
        # result_data_rf[f"VM{i}"] = rf_list
    print(json.dumps(result_data_lr))
    print("JSON_DATA_END")

def compression(list, batch_size=5):
    return [
        sum(list[i:i + batch_size]) / len(list[i:i + batch_size])
        for i in range(0, len(list), batch_size)
    ]

def random_forest(data, pred_name, prediction_length):
    from sklearn.ensemble import RandomForestRegressor

    n_lag = int(len(data) / 2)
    pred_horizon = int(prediction_length / 5)

    df = pd.DataFrame({'y': data})
    for i in range(1, n_lag + 1):
        df[f'lag_{i}'] = df['y'].shift(i)
    df.dropna(inplace=True)

    X = df[[f'lag_{i}' for i in range(1, n_lag + 1)]]
    y = df['y']

    model = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X, y)

    last_known = data[-n_lag:]
    preds = []

    for _ in range(pred_horizon):
        x_input = np.array(last_known[-n_lag:]).reshape(1, -1)
        y_pred = model.predict(x_input)[0]
        preds.append(y_pred)
        last_known.append(y_pred)

    plt.figure(figsize=(14, 6))
    plt.plot(range(len(data)), data, label="Original usage", color="blue")
    plt.plot(range(len(data), len(data) + pred_horizon), preds, label="Predicted usage", color="orange")
    plt.title("VM usage prediction")
    plt.xlabel("Time")
    plt.ylabel("Usage")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig(f"{pred_name}_prediction_plot.png")

    return preds

if __name__ == '__main__':
    do_pred()

