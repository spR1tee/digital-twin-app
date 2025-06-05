# Digital Twin and IoT-Cloud-Fog Simulator

This project demonstrates the integration of Digital Twin technology with IoT-Cloud-Fog computing concepts. The system consists of two Maven-based Java Spring Boot applications:

1. **[Dummy App](https://github.com/spR1tee/dummy-app)**: Simulates a real-world system by sending and receiving data via APIs.
2. **Digital Twin App**: Acts as the digital twin, storing data in a database, processing the data, and performing simulations and predictions.

## How to Run

1. Clone the Digital Twin repository
2. Clone the Dummy App repository from [here](https://github.com/spR1tee/dummy-app)
3. Open both projects in IntelliJ
4. Start the Digital Twin App by simply running **StartApplication.java**
5. Start the Dummy App by simply running **DummyAppApplication.java**

## Usage

After starting both of the apps, the Dummy App sends the JSON files from the data directory to the Digital Twin App.
The default sending schedule is set to 5 seconds, but this can be changed just as the sample JSON files.

## Requirements for Digital Twin App

To enable fog simulation and energy/resource calculations, the **[DISSECT-CF-Fog](https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog)** simulator JAR file must be added to the Digital Twin App project structure.

### Steps:

1. Clone or download the DISSECT-CF-Fog repository from [here](https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog)
2. Build the project to generate the JAR file (typically located in the `target/` directory)
3. Add the generated JAR to your Digital Twin App:

   * In IntelliJ:
     `File > Project Structure > Modules > Dependencies > + > JARs or directories > select the JAR file`
4. Apply the changes and rebuild the project

This enables the Digital Twin App to simulate fog computing scenarios using the DISSECT-CF-Fog engine.

---

Ha szeretnéd, hozzáadhatok egy `mvn install`-os vagy Ant-alapú build példát is a DISSECT-CF-Fog buildeléséhez.
