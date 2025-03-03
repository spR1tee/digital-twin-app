import sqlite3
import sys

import numpy as np
import pandas as pd
from sklearn.linear_model import LinearRegression

from predictor_model import PredictorModel


class LinearRegressionModel(PredictorModel):
    def __init__(self, simulation_settings):
        super().__init__("LINEAR_REGRESSION", simulation_settings)

    def predict(self, feature_name, dataframe, prediction_length, is_test_data):
        model = LinearRegression()
        model.fit(np.array(dataframe["timestamp"].values).reshape(-1, 1), dataframe["value1"].values)

        future_timestamps = PredictorModel.create_future_timestamp(dataframe=dataframe,
                                                                   prediction_length=prediction_length)
        result = model.predict(np.array(future_timestamps).reshape(-1, 1))

        return result.tolist()


if __name__ == '__main__':
    db_path = "../spring_db/database.db"

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
    paired_data = []

    for i in range(0, len(rows), 2):
        timestamp = rows[i][0]
        value1 = rows[i][1]
        value2 = rows[i + 1][1]
        paired_data.append((timestamp, value1))

    dataframe = pd.DataFrame(paired_data, columns=["timestamp", "value1"])

    dataframe["timestamp"] = pd.to_datetime(dataframe["timestamp"])

    # print(dataframe)

    simulation_settings = {}
    linear_regression_model = LinearRegressionModel(simulation_settings)

    feature_names = "value1"
    prediction_length = pred_length
    is_test_data = False

    predictions = linear_regression_model.predict(feature_names, dataframe, prediction_length, is_test_data)
    print(predictions)

    conn.close()
