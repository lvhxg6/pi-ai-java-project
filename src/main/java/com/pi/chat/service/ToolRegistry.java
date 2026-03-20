package com.pi.chat.service;

import com.pi.agent.types.AgentTool;
import com.pi.coding.tool.BashTool;
import com.pi.coding.tool.EditTool;
import com.pi.coding.tool.ReadTool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表，管理可用的 AgentTool 实例。
 * <p>
 * 使用 ConcurrentHashMap 保证线程安全，支持工具的注册、查找和获取。
 * 初始化时自动注册默认工具（bash、read、edit）。
 * <p>
 * Validates: Requirements 2.1, 2.5
 */
public class ToolRegistry {

    /**
     * 工具存储，使用 ConcurrentHashMap 保证线程安全。
     */
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    /**
     * 当前工作目录，用于初始化工具。
     */
    private final String cwd;

    /**
     * 创建 ToolRegistry 并注册默认工具。
     *
     * @param cwd 当前工作目录
     */
    public ToolRegistry(String cwd) {
        this.cwd = cwd;
        registerDefaultTools();
    }

    /**
     * 注册默认工具（bash、read、edit 等）。
     */
    private void registerDefaultTools() {
        register(new BashTool(cwd));
        register(new ReadTool(cwd));
        register(new EditTool(cwd));
        // 可扩展更多工具
    }

    /**
     * 注册工具。
     * <p>
     * 如果已存在同名工具，将被覆盖。
     *
     * @param tool 要注册的工具
     */
    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * 根据名称查找工具。
     *
     * @param name 工具名称
     * @return 包含工具的 Optional，如果不存在则返回空 Optional
     */
    public Optional<AgentTool> findByName(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有已注册的工具。
     *
     * @return 所有工具的列表副本
     */
    public List<AgentTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取所有工具名称。
     *
     * @return 所有工具名称的列表副本
     */
    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    /**
     * 获取当前工作目录。
     *
     * @return 当前工作目录
     */
    public String getCwd() {
        return cwd;
    }

    /**
     * 获取已注册工具的数量。
     *
     * @return 工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 检查是否存在指定名称的工具。
     *
     * @param name 工具名称
     * @return 如果存在返回 true，否则返回 false
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }
}
