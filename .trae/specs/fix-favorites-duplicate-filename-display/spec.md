# 修复 Favorites 文件名重复显示 Spec

## Why

收藏列表中同一文件名被显示两次，且路径信息对当前场景无必要，影响可读性与信息密度。需要将展示简化为“仅显示一次文件名”。

## What Changes

- 调整 Favorites 列表项文案拼装逻辑，确保文件名只展示一次。
- 移除 Favorites 列表中路径展示。
- 保留收藏项的打开、重命名、删除等行为不变。

## Impact

- 受影响规格：收藏列表显示能力
- 受影响代码：`FavoritesFragment` 及其对应列表适配/绑定逻辑

## ADDED Requirements

### Requirement: 收藏列表简化展示
系统 SHALL 在 Favorites 文件项中仅显示一次文件名，且不显示路径。

#### Scenario: 收藏项正常展示
- **WHEN** 用户进入 Favorites 页面
- **THEN** 每个文件项只出现一次文件名
- **AND** 列表中不展示该文件的路径文本

## MODIFIED Requirements

### Requirement: 收藏列表项文本组织
原有“文件名 + 路径（或重复文件名）”的组合展示，修改为仅保留单一文件名主展示，并移除路径展示位。
