# 多版本兼容性更新 - 变更摘要

## 分支信息
- **分支名称**: `feature/multi-version-compatibility`
- **提交哈希**: `b5b7f0e`
- **目标版本**: Paper/Spigot 1.18.x - 1.21.x
- **Java要求**: Java 17+

## 主要变更

### 1. 新增兼容层 (`src/main/java/com/invbackup/compat/`)
- **CompatibilityHelper.java**: 核心兼容性处理类
  - `getPotionEffect()`: 处理药水效果类型获取（兼容1.18-1.21）
  - `getModifierSlot()`: 处理属性修饰器槽位获取（兼容1.18-1.21）
  - 使用反射避免直接引用不存在的API类

### 2. 修改的核心文件

#### `BackupManager.java`
- 替换 `Registry.EFFECT.get()` 调用为兼容版本
- 修复 `Attribute.MAX_HEALTH` 常量兼容性问题（使用反射）
- 移除对 `EquipmentSlotGroup` 的直接引用

#### `RestoreGui.java`
- 替换 `Registry.EFFECT.get()` 调用为兼容版本
- 添加 `CompatibilityHelper` 导入

#### `InvBackupCommand.java`
- 降级Java 14+ switch表达式为传统switch语句
- 替换var关键字为显式类型声明

#### `plugin.yml`
- 将硬编码的 `api-version: "1.21"` 改为动态版本 `${API_VERSION}`

### 3. 构建系统更新

#### `build.gradle`
- 添加多版本构建支持
- 根据 `mcVersion` 参数选择不同Paper API
- 动态设置Java版本（1.18-1.21使用Java 17，1.21使用Java 21）

#### 构建脚本
- `build-all.bat`: Windows批量构建脚本
- `build-all.sh`: Linux/macOS构建脚本
- `build-versions.ps1`: PowerShell构建脚本

### 4. 文档更新

#### `README.md`
- 更新运行环境说明
- 添加多版本构建指南
- 说明支持的Minecraft版本

## 构建命令

```bash
# 构建特定版本
.\gradlew.bat clean build "-PmcVersion=1.18"  # 1.18.x版本
.\gradlew.bat clean build "-PmcVersion=1.19"  # 1.19.x版本
.\gradlew.bat clean build "-PmcVersion=1.20"  # 1.20.x版本
.\gradlew.bat clean build "-PmcVersion=1.21"  # 1.21.x版本（默认）

# 构建所有版本（Windows）
.\build-all.bat

# 构建所有版本（PowerShell）
powershell -ExecutionPolicy Bypass -File .\build-versions.ps1
```

## 生成的JAR文件

构建完成后，在 `build/libs/` 目录下会生成：
- `InvBackup-1.18.jar` - 1.18.x版本
- `InvBackup-1.19.jar` - 1.19.x版本
- `InvBackup-1.20.jar` - 1.20.x版本
- `InvBackup-1.21.jar` - 1.21.x版本

## 测试建议

### 需要重点测试的功能：
1. **药水效果恢复** - 验证所有药水效果类型都能正确恢复
2. **属性修饰器** - 验证物品属性修饰器正确保存和恢复
3. **最大生命值** - 验证玩家最大生命值正确记录
4. **GUI界面** - 验证所有GUI正常显示

### 测试环境：
- Paper 1.18.2 + Java 17
- Paper 1.19.4 + Java 17
- Paper 1.20.4 + Java 17
- Paper 1.21.4 + Java 21

## 向后兼容性

### 数据兼容性：
- ✅ 数据文件格式保持不变
- ✅ 可以跨版本迁移备份数据
- ✅ 配置文件格式兼容

### API兼容性：
- ✅ 插件API保持不变
- ✅ 命令和权限系统不变
- ✅ GUI界面功能完整

## 已知限制

1. **性能影响**：反射调用可能带来轻微性能开销
2. **编译警告**：某些API使用可能产生过时警告
3. **测试覆盖**：需要在真实环境中全面测试

## 下一步工作

1. **实际环境测试**：在真实服务器上验证所有功能
2. **性能优化**：优化反射调用，减少性能开销
3. **错误处理**：完善兼容层的错误处理和日志
4. **CI/CD集成**：设置自动化构建和测试流程
5. **文档完善**：添加详细的版本兼容性说明

## 提交信息

```
feat: 添加多版本兼容性支持 (1.18.x - 1.21.x)

- 创建兼容层 CompatibilityHelper 处理API差异
- 替换 Registry.EFFECT API 调用为兼容版本
- 修复 EquipmentSlotGroup 在1.18中的兼容性问题
- 修复 Attribute.MAX_HEALTH 常量兼容性
- 降级Java 14+语法（switch表达式、var关键字）
- 配置多版本Gradle构建系统
- 更新plugin.yml支持动态API版本
- 添加构建脚本和更新文档
- 支持构建：1.18.x, 1.19.x, 1.20.x, 1.21.x
```

## GitHub链接

- **分支**: https://github.com/Cc-Cece/InvBackup-plugin/tree/feature/multi-version-compatibility
- **Pull Request**: https://github.com/Cc-Cece/InvBackup-plugin/pull/new/feature/multi-version-compatibility
- **仓库新位置**: https://github.com/Cc-Cece/InvBackup-plugin.git