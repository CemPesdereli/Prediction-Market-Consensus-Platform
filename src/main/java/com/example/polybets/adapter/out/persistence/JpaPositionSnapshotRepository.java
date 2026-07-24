package com.example.polybets.adapter.out.persistence;

import com.example.polybets.adapter.out.persistence.entity.PositionSnapshotEntity;
import com.example.polybets.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaPositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, Long> {

    List<PositionSnapshotEntity> findByCategory(Category category);

    void deleteByCategory(Category category);
}
