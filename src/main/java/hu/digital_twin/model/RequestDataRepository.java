package hu.digital_twin.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestDataRepository extends JpaRepository<RequestData, Long> {
    RequestData findTopByOrderByIdDesc();
}
