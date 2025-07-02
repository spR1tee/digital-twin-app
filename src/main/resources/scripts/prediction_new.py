import json
import sqlite3
import sys
import os
import pandas as pd
import numpy as np
from error_metrics import ErrorMetrics
from sklearn.linear_model import LinearRegression
from predictor_model import PredictorModel
from statsmodels.tsa.arima.model import ARIMA
from pmdarima import auto_arima


class ArimaModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("ARIMA", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        feature_values = dataframe["feature"].values

        if self._simulation_settings["predictor"]["hyperparameters"].get("auto_optimize", False):
            model = auto_arima(
                feature_values,
                start_p=1, start_q=1,
                max_p=15, max_q=15,
                seasonal=False,
                stepwise=True,
                suppress_warnings=True
            )
            result = model.predict(n_periods=prediction_length)
        else:
            hyperparams = self._simulation_settings["predictor"]["hyperparameters"]
            order = (hyperparams["p_value"], hyperparams["d_value"], hyperparams["q_value"])

            model = ARIMA(feature_values, order=order)
            fitted = model.fit()
            result = fitted.forecast(prediction_length)

        return result.tolist()


class LinearRegressionModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("LINEAR_REGRESSION", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = LinearRegression()
        X = np.array(dataframe["timestamp"].values).reshape(-1, 1)
        y = dataframe["feature"].values

        model.fit(X, y)

        future_timestamps = PredictorModel.create_future_timestamp(
            dataframe=dataframe,
            prediction_length=prediction_length
        )
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist()


def get_database_data():
    """Adatbázisból adatok lekérése és paraméterek feldolgozása"""
    feature = sys.argv[1]
    based_on = int(sys.argv[2])
    pred_length = int(sys.argv[3])
    vm_count = int(sys.argv[4])
    tenant_id = sys.argv[5]

    # SQLite adatbázis elérési út
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_path = os.path.join(script_dir, "..", "db", f"{tenant_id}.db")

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # SQL lekérdezés a szükséges adatokhoz
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


def calculate_error_metrics(data):
    """Hibametrikák számítása"""
    train_test_split = int(len(data) * 3 / 4)  # 75%-os tanító és 25%-os teszt szétválasztás
    actual = [row[1] for row in data[train_test_split:]]
    train_data = data[:train_test_split]

    df = pd.DataFrame(train_data, columns=["timestamp", "feature"])
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    start_time = df["timestamp"].min()
    df["timestamp"] = (df["timestamp"] - start_time).dt.total_seconds()

    # Linear regression predikció a metrikákhoz
    lr_model = LinearRegressionModel({})
    predictions = lr_model.predict("feature", df, len(actual) * 5, False)

    print("MSE:", ErrorMetrics.MSE(actual, predictions))
    print("RMSE:", ErrorMetrics.RMSE(actual, predictions))
    print("MAE:", ErrorMetrics.MAE(actual, predictions))


def organize_data_by_vm(rows, based_on, vm_count):
    """Adatok szervezése VM-ek szerint"""
    vm_data = {}

    # Inicializálás
    for i in range(vm_count):
        vm_key = f"vm_{i}"
        vm_data[vm_key] = []

    if len(rows) < based_on * vm_count:
        print("Not enough data")
        sys.exit(1)

    rows.reverse()  # Időrendbe helyezés

    # Adatok felosztása VM-ek szerint
    for i in range(0, based_on * vm_count, vm_count):
        for j in range(vm_count):
            timestamp, value = rows[i + j][0], rows[i + j][1]
            vm_key = f"vm_{j}"
            vm_data[vm_key].append((timestamp, value))

    return vm_data


def prepare_dataframe(data_list):
    """DataFrame előkészítése predikciókhoz"""
    df = pd.DataFrame(data_list, columns=["timestamp", "feature"])
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    start_time = df["timestamp"].min()
    df["timestamp"] = (df["timestamp"] - start_time).dt.total_seconds()
    return df


def generate_predictions(vm_data, pred_length, model_type):
    """Predikciók generálása minden VM-re a választott modell szerint"""

    # ARIMA konfiguráció
    arima_settings = {
        "predictor": {
            "hyperparameters": {
                "p_value": 4,
                "d_value": 0,
                "q_value": 1,
                "auto_optimize": False
            }
        }
    }

    if model_type == "lr":
        model = LinearRegressionModel({})
    elif model_type == "arima":
        model = ArimaModel(arima_settings)
    elif model_type == "rf":
        model = None  # RF nem objektumorientált, külön metódus
    else:
        print("Unknown model type. Use: lr, arima, rf")
        sys.exit(1)

    predictions = {}

    for vm_key, data_list in vm_data.items():
        # Hibametrikák számítása minden VM-re
        calculate_error_metrics(data_list)

        # DataFrame előkészítése
        df = prepare_dataframe(data_list)

        if model_type == "rf":
            data = df["feature"].tolist()
            rf_predictions = random_forest_predict(data, pred_length)
            predictions[vm_key] = rf_predictions
        else:
            pred_list = model.predict("feature", df, pred_length, False)
            predictions[vm_key] = pred_list

    return predictions


def random_forest_predict(data, prediction_length):
    """Random Forest predikció implementáció"""
    from sklearn.ensemble import RandomForestRegressor

    n_lag = max(1, len(data) // 2)
    pred_horizon = max(1, prediction_length // 5)

    # Lag features létrehozása
    df = pd.DataFrame({'y': data})
    for i in range(1, n_lag + 1):
        df[f'lag_{i}'] = df['y'].shift(i)
    df.dropna(inplace=True)

    if len(df) == 0:
        return [data[-1]] * pred_horizon

    X = df[[f'lag_{i}' for i in range(1, n_lag + 1)]]
    y = df['y']

    model = RandomForestRegressor(n_estimators=100, max_depth=10, random_state=42)
    model.fit(X, y)

    # Predikciók generálása
    last_known = data[-n_lag:]
    predictions = []

    for _ in range(pred_horizon):
        x_input = np.array(last_known[-n_lag:]).reshape(1, -1)
        y_pred = model.predict(x_input)[0]
        predictions.append(y_pred)
        last_known.append(y_pred)

    # Grafikon készítése
    '''
    plt.figure(figsize=(14, 6))
    plt.plot(range(len(data)), data, label="Original usage", color="blue")
    plt.plot(range(len(data), len(data) + pred_horizon), predictions, label="Predicted usage", color="orange")
    plt.title("VM usage prediction")
    plt.xlabel("Time")
    plt.ylabel("Usage")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig(f"vm_prediction_plot.png")
    '''

    return predictions


def compression(data_list, batch_size=5):
    """Adatok tömörítése átlagolással"""
    return [
        sum(data_list[i:i + batch_size]) / len(data_list[i:i + batch_size])
        for i in range(0, len(data_list), batch_size)
    ]


def main():
    """Főprogram"""
    # Adatok lekérése
    rows, based_on, pred_length, vm_count = get_database_data()

    # Előrejelzés típusa
    model_type = sys.argv[6].lower()

    # Adatok szervezése VM-ek szerint
    vm_data = organize_data_by_vm(rows, based_on, vm_count)

    # Predikciók generálása
    predictions = generate_predictions(vm_data, pred_length, model_type)

    # Eredmények kiírása
    for i, (vm_key, pred_list) in enumerate(predictions.items()):
        print(f"{model_type.upper()} predictions for {vm_key}: {pred_list}")

    # JSON formátumú kimenet
    print("JSON_DATA_START")
    result_data = {f"VM{i}": pred_list for i, (_, pred_list) in enumerate(predictions.items())}
    print(json.dumps(result_data))
    print("JSON_DATA_END")


if __name__ == '__main__':
    main()