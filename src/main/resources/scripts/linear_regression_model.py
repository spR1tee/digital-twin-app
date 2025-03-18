import sqlite3
import sys
import os

import numpy as np
import pandas as pd
from error_metrics import ErrorMetrics
from sklearn.linear_model import LinearRegression

from predictor_model import PredictorModel


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
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_path = os.path.join(script_dir, "../spring_db/database.db")

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    feature = sys.argv[1]
    based_on = int(sys.argv[2])
    pred_length = int(sys.argv[3])

    query = f"""
    SELECT rd.timestamp, vd.{feature}
    FROM request_data rd
    LEFT JOIN vm_data vd ON rd.id = vd.request_data_id;
    """

    cursor.execute(query)
    rows = cursor.fetchall()
    conn.close()

    return rows, based_on, pred_length


def error_metrics(data):
    index = int(len(data) * 3 / 4)
    actual = [tuple[1] for tuple in data[index:]]
    train_data = data[:index]

    metrics_dataframe = pd.DataFrame(train_data, columns=["timestamp", "feature"])

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "feature_for_metrics"
    prediction_length = len(actual)
    is_test_data = False

    predictions = linear_regression_model.predict(feature_names, metrics_dataframe, prediction_length, is_test_data)
    # print(predictions)
    # print(actual)

    print("MSE:")
    print(ErrorMetrics.MSE(actual, predictions))

    print("RMSE:")
    print(ErrorMetrics.RMSE(actual, predictions))

    print("MAE:")
    print(ErrorMetrics.MAE(actual, predictions))


def do_pred():
    rows, based_on, pred_length = db_connect()
    paired_data = []
    tmp = 0
    print(len(rows))
    rows.reverse()

    if (len(rows) >= based_on * 2):
        for i in range(0, based_on * 2, 2):
            timestamp = tmp
            # timestamp = rows[i][0]
            value1 = rows[i][1]
            # value2 = rows[i + 1][1]
            paired_data.append((timestamp, value1))
            tmp += 1
    else:
        print("Not enough data")

    error_metrics(paired_data)

    dataframe = pd.DataFrame(paired_data, columns=["timestamp", "feature"])

    # dataframe["timestamp"] = pd.to_datetime(dataframe["timestamp"])

    # print(dataframe)

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "feature"
    prediction_length = pred_length
    is_test_data = False

    predictions = linear_regression_model.predict(feature_names, dataframe, prediction_length, is_test_data)
    # predictions = np.clip(predictions, 0, 1)
    print("Requested predictions:")
    print(predictions)


if __name__ == '__main__':
    do_pred()
