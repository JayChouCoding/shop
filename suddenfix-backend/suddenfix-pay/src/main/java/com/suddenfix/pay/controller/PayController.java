package com.suddenfix.pay.controller;

import com.suddenfix.common.result.Result;
import com.suddenfix.pay.domain.pojo.Pay;
import com.suddenfix.pay.service.IPayService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/pay", "/api/pay"})
public class PayController {

    private final IPayService payService;

    @GetMapping("/order/{orderId}")
    public Result<Pay> queryPay(@PathVariable("orderId") Long orderId,
                                @RequestHeader(value = "user_id", required = false) Long userId) {
        return payService.queryPay(orderId, userId);
    }

    @GetMapping(value = {"/alipay/page/{orderId}", "/createAlipayPage/{orderId}"}, produces = MediaType.TEXT_HTML_VALUE)
    public String createAlipayPage(@PathVariable("orderId") Long orderId,
                                   @RequestHeader(value = "user_id", required = false) Long userId) {
        return payService.createAlipayPage(orderId, userId);
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        return payService.handleAlipayNotify(extractParams(request));
    }

    @GetMapping(value = "/success", produces = MediaType.TEXT_HTML_VALUE)
    public String paySuccessReturn(@RequestParam(value = "orderId", required = false) Long orderId,
                                   @RequestParam(value = "out_trade_no", required = false) String outTradeNo) {
        return payService.buildPaySuccessPage(orderId, outTradeNo);
    }

    @PostMapping("/mock/create")
    public Result<Pay> createMockPay(@RequestParam("orderId") Long orderId,
                                     @RequestHeader(value = "user_id", required = false) Long userId) {
        return payService.createMockPay(orderId, userId);
    }

    @PostMapping("/mock/notify")
    public String mockNotify(HttpServletRequest request) {
        return payService.handleMockNotify(extractParams(request));
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = valueStr + (i == values.length - 1 ? values[i] : values[i] + ",");
            }
            params.put(name, valueStr);
        }
        return params;
    }
}
