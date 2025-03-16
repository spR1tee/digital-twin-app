import math


class ErrorMetrics:

    @staticmethod
    def RMSE(actual, prediction):
        sum = 0

        # test_vals = actual["data"].values
        # pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += ((actual[i] - prediction[i]) ** 2) / len(actual)
        return math.sqrt(sum)

    @staticmethod
    def MAE(actual, prediction):
        sum = 0

        # test_vals = actual["data"].values
        # pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += abs(actual[i] - prediction[i])
        return (1 / len(actual)) * sum

    @staticmethod
    def MSE(actual, prediction):
        sum = 0

        # test_vals = actual["data"].values
        # pred_vals = prediction["data"].values

        for i in range(0, len(actual)):
            sum += (actual[i] - prediction[i]) ** 2
        return (1 / len(actual)) * sum
