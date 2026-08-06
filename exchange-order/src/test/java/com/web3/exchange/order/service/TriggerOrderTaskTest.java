package com.web3.exchange.order.service;

import com.web3.exchange.order.constant.OrderConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件单触发判定单测（docs/advanced-orders.md §三）。
 * <p>止盈=最新价>=触发价；止损=最新价<=触发价；边界相等即触发。</p>
 */
class TriggerOrderTaskTest {

    @Test
    void takeProfit_triggersWhenLastAtOrAbove() {
        // 止盈：触发价 50000，最新价 >= 50000 才激活
        assertFalse(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_TAKE_PROFIT, 50000L, 49999L));
        assertTrue(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_TAKE_PROFIT, 50000L, 50000L));
        assertTrue(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_TAKE_PROFIT, 50000L, 50100L));
    }

    @Test
    void stopLoss_triggersWhenLastAtOrBelow() {
        // 止损：触发价 30000，最新价 <= 30000 才激活
        assertFalse(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_STOP_LOSS, 30000L, 30100L));
        assertTrue(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_STOP_LOSS, 30000L, 30000L));
        assertTrue(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_STOP_LOSS, 30000L, 29900L));
    }

    @Test
    void invalidOrNull_neverTriggers() {
        assertFalse(TriggerOrderTask.matchesTrigger(null, 50000L, 60000L));
        assertFalse(TriggerOrderTask.matchesTrigger(OrderConstant.TRIGGER_TYPE_TAKE_PROFIT, null, 60000L));
        assertFalse(TriggerOrderTask.matchesTrigger(99, 50000L, 60000L));
    }
}
