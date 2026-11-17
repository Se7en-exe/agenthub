package com.lws.agenthub.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端单页应用的路由回退
 *
 * <p>前端用的是 HTML5 History 路由（createWebHistory），直接访问 /assistant 这类地址时，
 * 请求会打到后端而不是静态文件。这里把它们统一转发回 index.html，交给前端路由处理，
 * 否则刷新页面就是 404。
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/assistant", "/agent"})
    public String forward() {
        return "forward:/index.html";
    }
}