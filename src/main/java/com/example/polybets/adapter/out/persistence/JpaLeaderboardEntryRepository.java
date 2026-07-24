package com.example.polybets.adapter.out.persistence;

import com.example.polybets.adapter.out.persistence.entity.LeaderboardEntryEntity;
import com.example.polybets.domain.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface JpaLeaderboardEntryRepository extends JpaRepository<LeaderboardEntryEntity, Long> {

    List<LeaderboardEntryEntity> findByCategoryOrderByRankAsc(Category category);

    void deleteByCategory(Category category);
}
