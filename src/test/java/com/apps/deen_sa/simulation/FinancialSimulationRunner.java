package com.apps.deen_sa.simulation;

import com.apps.deen_sa.conversation.ConversationContext;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

/**
 * Simple fluent simulation runner for tests. Records actions and executes them in order.
 */
public class FinancialSimulationRunner {

    private final FinancialSimulationContext ctx;
    private final List<Runnable> actions = new ArrayList<>();
    private LocalDate currentDay;
    private final List<String> replayLog = new ArrayList<>();

    private FinancialSimulationRunner(FinancialSimulationContext ctx) {
        this.ctx = ctx;
        this.currentDay = ctx.getCurrentDate();
    }

    public static FinancialSimulationRunner simulate(FinancialSimulationContext ctx) {
        return new FinancialSimulationRunner(ctx);
    }

    public FinancialSimulationRunner day(int dayOfMonth) {
        // set the simulation date to the requested day within current month
        LocalDate base = ctx.getCurrentDate();
        this.currentDay = base.withDayOfMonth(Math.min(dayOfMonth, base.lengthOfMonth()));
        return this;
    }

    public FinancialSimulationRunner expense(String description, long amount, String sourceAccount) {
        LocalDate actionDate = this.currentDay;
        actions.add(() -> {
            String text = String.format(
                    "SIM:EXPENSE;amount=%d;desc=%s;source=%s;date=%s",
                    amount, description, sourceAccount, actionDate.toString()
            );
            ctx.expenseHandler.handleSpeech(text, new ConversationContext());
        });
        replayLog.add(String.format("Day %s: EXPENSE %d %s (%s)", actionDate.getDayOfMonth(), amount, sourceAccount, description));
        return this;
    }

    public FinancialSimulationRunner payCreditCard(long amount, String targetName) {
        LocalDate actionDate = this.currentDay;
        actions.add(() -> {
            String text = String.format(
                    "SIM:PAYMENT;amount=%d;target= CREDIT_CARD;targetName=%s;date=%s;source=BANK_ACCOUNT",
                    amount, targetName, actionDate.toString()
            );
            ctx.liabilityPaymentHandler.handleSpeech(text, new ConversationContext());
        });
        replayLog.add(String.format("Day %s: PAY_CREDIT_CARD %d %s", actionDate.getDayOfMonth(), amount, targetName));
        return this;
    }

    public FinancialSimulationRunner setupContainer(String type, String name, long currentValue) {
        LocalDate actionDate = this.currentDay;
        actions.add(() -> {
            String text = String.format(
                    "SIM:ACCOUNT;type=%s;name=%s;current=%d;date=%s",
                    type, name, currentValue, actionDate.toString()
            );
            ctx.accountSetupHandler.handleSpeech(text, new ConversationContext());
        });
        replayLog.add(String.format("Day %s: SETUP %s %s %d", actionDate.getDayOfMonth(), type, name, currentValue));
        return this;
    }

    public void run() {
        for (Runnable r : actions) {
            r.run();
        }
    }

    public void runScenario(List<Runnable> scenarioActions) {
        for (Runnable r : scenarioActions) {
            r.run();
        }
    }

    public List<String> getReplayLog() {
        return List.copyOf(replayLog);
    }
}
