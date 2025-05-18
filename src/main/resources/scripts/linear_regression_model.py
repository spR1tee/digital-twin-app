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

class LinearRegressionModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("LINEAR_REGRESSION", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = LinearRegression()
        model.fit(np.array(dataframe["timestamp"].values).reshape(-1, 1), dataframe["feature"].values)

        future_timestamps = PredictorModel.create_future_timestamp(dataframe=dataframe,
                                                                   prediction_length=prediction_length)
        timestamps_copy = future_timestamps.copy()
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist(), timestamps_copy


def db_connect():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    db_path = os.path.join(script_dir, "../spring_db/database.db")

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    feature = sys.argv[1]
    based_on = int(sys.argv[2])
    pred_length = int(sys.argv[3])
    vm_count = int(sys.argv[4])

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
    index = int(len(data) * 3 / 4)
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

    predictions, future_timestamps = linear_regression_model.predict(feature_names, df, prediction_length, is_test_data)

    print("MSE:")
    print(ErrorMetrics.MSE(actual, predictions))

    print("RMSE:")
    print(ErrorMetrics.RMSE(actual, predictions))

    print("MAE:")
    print(ErrorMetrics.MAE(actual, predictions))


def do_pred():
    rows, based_on, pred_length, vm_count = db_connect()
    paired_data_lists = {}
    predictions_list = {}

    for i in range(vm_count):
        paired_data_lists[f"paired_data{i if i > 0 else ''}"] = []
        predictions_list[f"prediction{i if i > 0 else ''}"] = []

    tmp = 0
    print(len(rows))
    rows.reverse()
    print(rows)

    if len(rows) >= based_on * vm_count:
        for i in range(0, based_on * vm_count, vm_count):
            for j in range(vm_count):
                value = rows[i + j][1]
                timestamp = rows[i + j][0]
                list_name = f"paired_data{j if j > 0 else ''}"
                paired_data_lists[list_name].append((timestamp, value))
    else:
        print("Not enough data")

    for list_name in paired_data_lists.keys():
        error_metrics(paired_data_lists[list_name])

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "feature"
    prediction_length = pred_length
    is_test_data = False

    for i in range(vm_count):
        list_name = f"paired_data{i if i > 0 else ''}"
        data_list = paired_data_lists[list_name]
        df = pd.DataFrame(data_list, columns=["timestamp", "feature"])
        df["timestamp"] = pd.to_datetime(df["timestamp"])
        df["usage_smooth"] = df["feature"].rolling(window=60).mean()
        start_time = df["timestamp"].min()
        df["timestamp"] = (df["timestamp"] - start_time).dt.total_seconds()
        '''plt.figure(figsize=(12, 5))
        plt.plot(df["timestamp"], df["feature"], label="Eredeti", alpha=0.5)
        plt.plot(df["timestamp"], df["usage_smooth"], label="Mozgóátlag", linewidth=2)
        plt.xlabel("Time")
        plt.ylabel("Usage")
        plt.title("VM usage mozgóátlaggal")
        plt.legend()
        plt.grid()
        plt.savefig(f"{list_name}mozgoatlag.png", dpi=300)

        plt.figure(figsize=(10, 5))
        plt.plot(df['timestamp'], df['feature'], marker='o', label='VM usage')
        plt.title('VM usage')
        plt.xlabel('Time')
        plt.ylabel('Usage')
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        plt.savefig(f"{list_name}.png", dpi=300)'''

        pred_name = f"prediction{i if i > 0 else ''}"
        df["feature"] = df["feature"].rolling(window=60).mean()
        df["feature"] = df["feature"].fillna(0)
        print(df)
        predictions_list[pred_name], timestamps_copy = linear_regression_model.predict(feature_names,
                                                                      df,
                                                                      prediction_length,
                                                                      is_test_data)
        '''plt.figure(figsize=(10, 5))
        plt.plot(timestamps_copy, predictions_list[pred_name], marker='o', label='VM usage')
        plt.title('Predicted VM usage')
        plt.xlabel('Time')
        plt.ylabel('Usage')
        plt.grid(True)
        plt.legend()
        plt.tight_layout()
        plt.savefig(f"{pred_name}.png", dpi=300)'''

    for i in range(vm_count):
        list_name = f"prediction{i if i > 0 else ''}"
        data_list = predictions_list[list_name]
        print(len(data_list))
        print(f"Predictions for vm{i}:\n{data_list}")

    print("JSON_DATA_START")
    result_data = {}
    for i in range(vm_count):
        list_name = f"prediction{i if i > 0 else ''}"
        data_list = predictions_list[list_name]
        result_data[f"VM{i}"] = data_list
    print(json.dumps(result_data))
    print("JSON_DATA_END")


if __name__ == '__main__':
    do_pred()
