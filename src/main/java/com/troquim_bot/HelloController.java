package com.troquim_bot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Troquim Online 🚀";
    }

    @GetMapping("/status")
    public String status() {
        return "Online";
    }

    @GetMapping("/sobre")
    public String sobre() {
        return "Troquim Bot v1";
    }


        @GetMapping("/soma/{n1}/{n2}")
        public int soma(@PathVariable int n1, @PathVariable int n2){
            return n1+n2;
    }
}


