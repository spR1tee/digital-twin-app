CREATE TABLE IF NOT EXISTS request_data (
                                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                                            request_type TEXT,
                                            timestamp TEXT,
                                            vms_count INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS vm_data (
                                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                                       core_processing_power REAL NOT NULL,
                                       cpu INTEGER NOT NULL,
                                       data_since_last_save INTEGER NOT NULL,
                                       name TEXT,
                                       network_traffic INTEGER NOT NULL,
                                       price_per_tick REAL NOT NULL,
                                       ram INTEGER NOT NULL,
                                       req_disk INTEGER NOT NULL,
                                       startup_process INTEGER NOT NULL,
                                       status TEXT,
                                       type TEXT,
                                       usage REAL NOT NULL,
                                       request_data_id INTEGER,
                                       FOREIGN KEY (request_data_id) REFERENCES request_data(id) ON DELETE CASCADE
    );
