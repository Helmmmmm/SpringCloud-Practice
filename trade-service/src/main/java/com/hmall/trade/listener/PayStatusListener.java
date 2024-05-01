package com.hmall.trade.listener;

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
public class PayStatusListener {

    private final IOrderService orderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "trade.pay.success.queue", durable = "true"), // durable持久化到磁盘
            exchange = @Exchange(name = "pay.direct"), // type默认就是direct，可以不用指定
            key = "pay.success"
    ))
    public void listenPaySuccess(Long orderId) {
        // 1、查询订单
        Order order = orderService.getById(orderId);

        // 2、判断订单状态，是否为未支付
        if (order == null || order.getStatus() != 1) {
            return; // 不作处理
        }

        // 3、标记订单状态为已支付
        orderService.markOrderPaySuccess(orderId);
    }
}
