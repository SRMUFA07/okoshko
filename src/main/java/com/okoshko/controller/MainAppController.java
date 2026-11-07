package com.okoshko.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class MainAppController {

    @GetMapping(value = "/app", produces = MediaType.TEXT_HTML_VALUE)
    public String showApp() throws IOException {
        return page("app.html");
    }

    @GetMapping(value = "/admin", produces = MediaType.TEXT_HTML_VALUE)
    public String showAdmin() throws IOException {
        return page("static/admin.html");
    }

    @GetMapping(value = "/teacher", produces = MediaType.TEXT_HTML_VALUE)
    public String showTeacher() throws IOException {
        return page("static/teacher.html");
    }

    private String page(String name) throws IOException {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }
}
