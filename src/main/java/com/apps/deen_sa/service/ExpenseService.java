package com.apps.deen_sa.service;

import com.apps.deen_sa.entity.ExpenseEntity;
import com.apps.deen_sa.repo.ExpenseRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ExpenseService {
    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public List<ExpenseEntity> getAllExpenses() {
        return expenseRepository.findAll();
    }
}

