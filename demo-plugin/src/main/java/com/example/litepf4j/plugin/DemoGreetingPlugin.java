package com.example.litepf4j.plugin;

import com.example.litepf4j.core.Extension;
import com.example.litepf4j.demo.api.GreetingExtension;

@Extension(ordinal = 100)
public class DemoGreetingPlugin implements GreetingExtension {
    @Override
    public String greet(String input) {
        return "[demo-greeting-plugin] hello, " + input + " from zipped plugin";
    }
}
