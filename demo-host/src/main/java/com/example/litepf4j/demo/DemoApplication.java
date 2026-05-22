package com.example.litepf4j.demo;

import com.example.litepf4j.core.PluginManager;
import com.example.litepf4j.core.PluginWrapper;
import com.example.litepf4j.demo.api.GreetingExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DemoApplication {
    public static void main(String[] args) {
        Path pluginsDirectory = Path.of("plugins").toAbsolutePath();
        PluginManager pluginManager = new PluginManager(pluginsDirectory);
        pluginManager.loadPlugins();

        System.out.println("扫描目录: " + pluginsDirectory);
        for (PluginWrapper plugin : pluginManager.getPlugins()) {
            System.out.printf("已加载插件: id=%s, version=%s, provider=%s%n",
                    plugin.descriptor().pluginId(),
                    plugin.descriptor().version(),
                    plugin.descriptor().provider());
        }

        Map<String, List<GreetingExtension>> groupedExtensions = pluginManager.getExtensionsByPlugin(GreetingExtension.class);
        groupedExtensions.forEach((pluginId, extensions) -> {
            System.out.println("插件 " + pluginId + " 注册的 GreetingExtension 数量: " + extensions.size());
            for (GreetingExtension extension : extensions) {
                System.out.println("调用结果: " + extension.greet("lite-plugin-framework"));
            }
        });

        if (groupedExtensions.isEmpty()) {
            throw new IllegalStateException("没有发现任何 GreetingExtension 扩展实现");
        }
    }
}
