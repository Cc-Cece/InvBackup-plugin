package com.invbackup.compat;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

/**
 * 兼容性辅助类，用于处理不同Minecraft版本间的API差异
 * 目标：支持 1.18.x - 1.21.x
 */
public class CompatibilityHelper {
    
    private CompatibilityHelper() {
        // 工具类，不需要实例化
    }
    
    /**
     * 获取药水效果类型（兼容1.18-1.21）
     * 
     * @param name 药水效果名称（如"speed"、"regeneration"）
     * @return 药水效果类型，如果找不到返回null
     */
    public static PotionEffectType getPotionEffect(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        
        // 统一转换为小写，去除可能的空格
        String normalized = name.toLowerCase().trim();
        
        try {
            // 方法1：尝试1.19+的Registry API（如果可用）
            // 使用反射检查Registry类是否存在，避免直接引用
            try {
                Class<?> registryClass = Class.forName("org.bukkit.Registry");
                Object effectRegistry = registryClass.getField("EFFECT").get(null);
                java.lang.reflect.Method getMethod = effectRegistry.getClass().getMethod("get", NamespacedKey.class);
                
                NamespacedKey key = NamespacedKey.minecraft(normalized);
                Object result = getMethod.invoke(effectRegistry, key);
                if (result instanceof PotionEffectType) {
                    return (PotionEffectType) result;
                }
            } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
                // Registry API不可用，继续尝试其他方法
            }
            
            // 方法2：尝试1.13+的getByKey方法
            try {
                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(normalized));
                if (type != null) {
                    return type;
                }
            } catch (Exception e) {
                // getByKey可能失败，继续尝试
            }
            
            // 方法3：使用传统的getByName方法
            // 需要将小写名称转换为大写（如"speed" -> "SPEED"）
            String upperName = normalized.toUpperCase();
            
            // 处理一些常见的命名差异
            if (upperName.equals("INSTANT_HEAL")) {
                upperName = "HEAL";
            } else if (upperName.equals("INSTANT_DAMAGE")) {
                upperName = "HARM";
            } else if (upperName.equals("JUMP_BOOST")) {
                upperName = "JUMP";
            }
            
            return PotionEffectType.getByName(upperName);
            
        } catch (Exception e) {
            // 所有方法都失败，返回null
            return null;
        }
    }
    
    /**
     * 获取属性修饰器的槽位信息（兼容1.18-1.21）
     * 
     * @param modifier 属性修饰器
     * @return 槽位名称，如果无法获取返回null
     */
    public static String getModifierSlot(AttributeModifier modifier) {
        if (modifier == null) {
            return null;
        }
        
        try {
            // 方法1：尝试1.21+的getSlotGroup方法（使用反射，避免直接引用）
            try {
                // 使用反射检查getSlotGroup方法是否存在
                java.lang.reflect.Method getSlotGroupMethod = modifier.getClass().getMethod("getSlotGroup");
                Object group = getSlotGroupMethod.invoke(modifier);
                if (group != null) {
                    return group.toString();
                }
            } catch (NoSuchMethodException e) {
                // getSlotGroup方法不存在，继续尝试
            } catch (Exception e) {
                // 其他异常，继续尝试
            }
            
            // 方法2：使用1.16+的getSlot方法
            try {
                EquipmentSlot slot = modifier.getSlot();
                if (slot != null) {
                    return slot.name();
                }
            } catch (NoSuchMethodError e) {
                // getSlot方法也不存在
            }
            
            // 方法3：尝试通过反射获取旧版本的槽位信息
            try {
                // 旧版本可能使用不同的方法名或字段
                java.lang.reflect.Method getSlotMethod = modifier.getClass().getMethod("getSlot");
                Object slot = getSlotMethod.invoke(modifier);
                if (slot != null) {
                    return slot.toString();
                }
            } catch (Exception e) {
                // 反射也失败
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 检查当前服务器是否支持1.21+的EquipmentSlotGroup API
     * 
     * @return 如果支持返回true，否则返回false
     */
    public static boolean supportsEquipmentSlotGroup() {
        try {
            // 尝试访问EquipmentSlotGroup类
            Class.forName("org.bukkit.inventory.EquipmentSlotGroup");
            // 尝试调用getSlotGroup方法
            AttributeModifier.class.getMethod("getSlotGroup");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
    }
    
    /**
     * 检查当前服务器是否支持1.19+的Registry API
     * 
     * @return 如果支持返回true，否则返回false
     */
    public static boolean supportsRegistryAPI() {
        try {
            Class.forName("org.bukkit.Registry");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 获取服务器的主要版本号（如18、19、20、21）
     * 
     * @return 主版本号，如果无法获取返回-1
     */
    public static int getMajorVersion() {
        try {
            String version = org.bukkit.Bukkit.getVersion();
            // 版本字符串示例：git-Paper-1.18.2-R0.1-SNAPSHOT
            if (version.contains("1.18")) return 18;
            if (version.contains("1.19")) return 19;
            if (version.contains("1.20")) return 20;
            if (version.contains("1.21")) return 21;
            
            // 尝试从Bukkit版本API获取
            String bukkitVersion = org.bukkit.Bukkit.getBukkitVersion();
            if (bukkitVersion.startsWith("1.18")) return 18;
            if (bukkitVersion.startsWith("1.19")) return 19;
            if (bukkitVersion.startsWith("1.20")) return 20;
            if (bukkitVersion.startsWith("1.21")) return 21;
            
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }
}