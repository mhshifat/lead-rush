package com.leadrush.sequence.repository;

import com.leadrush.sequence.entity.SequenceStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SequenceStepRepository extends JpaRepository<SequenceStep, UUID> {

    List<SequenceStep> findBySequenceIdOrderByStepOrderAsc(UUID sequenceId);
}
