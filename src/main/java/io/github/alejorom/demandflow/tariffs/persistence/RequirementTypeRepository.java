package io.github.alejorom.demandflow.tariffs.persistence;

import java.util.List;
import java.util.Optional;

import io.github.alejorom.demandflow.tariffs.domain.RequirementType;
import org.springframework.data.repository.Repository;

public interface RequirementTypeRepository extends Repository<RequirementType, Short> {
    Optional<RequirementType> findById(Short id);
    List<RequirementType> findAllByOrderByIdAsc();
}
