package com.hmall.trade.listener;

import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderDelayMessageListener {

    private final IOrderService orderService;
    private final PayClient payClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(MQConstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.DELAY_EXCHANGE_NAME, delayed = "true"),
            key = MQConstants.DELAY_ORDER_KEY
    ))
    public void listenOrderDelayMessage(Long orderId) {
        // 1、查询订单(本地)
        Order order = orderService.getById(orderId);

        // 2、检测订单状态，判断是否已支付
        if (order == null || order.getStatus() != 1) {
            return; // 订单不存在 或 已支付，则直接return
        }

        // 3、未支付，则需要查询支付流水状态(远程调用)
        PayOrderDTO payOrder = payClient.queryPayOrderByBizOrderNo(orderId);

        // 4、判断是否支付：4.1 已支付，标记订单状态为已支付；4.2 未支付，关闭订单，恢复库存
        if (payOrder != null && payOrder.getStatus() == 3) {
            orderService.markOrderPaySuccess(orderId);
        } else {
            orderService.cancelOrder(orderId);
        }
    }
}
