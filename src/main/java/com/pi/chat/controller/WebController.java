package com.pi.chat.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Web controller for serving Thymeleaf pages.
 */
@Controller
public class WebController {
    
    @GetMapping("/")
    public String index() {
        return "index";
    }
    
    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }
    
    @GetMapping("/skills")
    public String skills() {
        return "skills";
    }
}
