# Digital Twin and IoT-Fog-Cloud Simulator

This project demonstrates the integration of Digital Twin technology with IoT-Fog-Cloud computing concepts. The system consists of two Maven-based Java Spring Boot applications:

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

To enable fog simulation and energy/resource calculations, the **[DISSECT-CF-Fog](https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog)** simulator must be added as a Maven dependency to the Digital Twin App project.

### Steps:

1. Clone or download the DISSECT-CF-Fog repository from [here](https://github.com/sed-inf-u-szeged/DISSECT-CF-Fog)

2. Build the project to generate the JAR file:
   ```bash
   mvn clean package
   ```

3. Install the JAR to your local Maven repository:
   ```bash
   mvn install:install-file -Dfile=path/to/dissect-cf-fog-1.0.jar -DgroupId=hu.u-szeged.inf.fog -DartifactId=dissect-cf-fog -Dversion=1.0 -Dpackaging=jar
   ```

4. Add the dependency to your Digital Twin App's `pom.xml`:
   ```xml
   <dependency>
       <groupId>hu.u-szeged.inf.fog</groupId>
       <artifactId>dissect-cf-fog</artifactId>
       <version>1.0</version>
   </dependency>
   ```

5. Refresh Maven dependencies and rebuild the project

This enables the Digital Twin App to simulate fog computing scenarios using the DISSECT-CF-Fog engine through Maven dependency management.
