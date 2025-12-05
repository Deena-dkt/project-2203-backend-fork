package com.apps.deen_sa.repo;

import com.apps.deen_sa.entity.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    // Basic CRUD and findAll provided by JpaRepository
}

