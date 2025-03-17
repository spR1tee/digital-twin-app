# Digital Twin and IoT-Cloud-Fog Simulator

This project demonstrates the integration of Digital Twin technology with IoT-Cloud-Fog computing concepts. The system consists of two Maven-based Java Spring Boot applications:
1. **[Dummy App](https://github.com/spR1tee/dummy-app)**: Simulates a real-world system by sending and receiving data via APIs.
2. **Digital Twin App**: Acts as the digital twin, storing data in a database, processing the data, and performing simulations and predictions.

## How to Run
1. Clone the Digital Twin repository
2. Clone the Dummy App repository from [here](https://github.com/spR1tee/dummy-app)
3. Open both projects in Intellij
4. Start the Digital Twin App by simply running **StartApplication.java**
5. Start the Dummy app by simply running **DummyAppApplication.java**

## Usage
After starting both of the apps, the Dummy App sends the JSON files from the data directory to the Digital Twin App.
The default sending schedule is set to 5 seconds, but this can be changed just as the sample JSON files.
