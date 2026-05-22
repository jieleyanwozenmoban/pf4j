package com.example.litepf4j.demo.api;

import com.example.litepf4j.core.ExtensionPoint;

public interface GreetingExtension extends ExtensionPoint {
    String greet(String input);
}
