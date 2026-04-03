# Favorites 目录类型与打开行为修复 Spec

## Why
当前将目录添加到 Favorites 后，列表中会被错误标记为 `[FILE]`，并且点击后走了文件打开路径，导致无法进入目录。该问题会破坏收藏目录作为快捷入口的核心体验。

## What Changes
- 修复 Favorites 条目类型判定逻辑，确保目录在 Favorites 中保持目录类型。
- 修复 Favorites 点击行为分发，目录进入浏览路径、文件进入打开路径。
- 统一 Favorites 展示标签与实际类型，避免出现显示与行为不一致。

## Impact
- Affected specs: Favorites 列表类型展示、Favorites 条目点击行为
- Affected code: `FavoritesFragment.kt`、Favorites 数据模型/映射逻辑、目录/文件打开分发逻辑

## ADDED Requirements
### Requirement: Favorites 目录类型保持正确
系统 SHALL 在目录被添加到 Favorites 后，保留其目录类型并在列表中按目录展示。

#### Scenario: 收藏目录后查看列表
- **WHEN** 用户将一个目录添加到 Favorites
- **THEN** 该条目在 Favorites 中显示为目录标签（而非 `[FILE]`）

### Requirement: Favorites 点击行为与类型一致
系统 SHALL 根据 Favorites 条目类型执行正确的打开行为。

#### Scenario: 点击收藏目录
- **WHEN** 用户点击 Favorites 中的目录条目
- **THEN** 进入该目录浏览页面

#### Scenario: 点击收藏文件
- **WHEN** 用户点击 Favorites 中的文件条目
- **THEN** 打开该文件（保持现有行为）

## MODIFIED Requirements
### Requirement: Favorites 条目展示与交互一致性
原有 Favorites 可能存在“显示为文件但实际是目录”的不一致行为，修改为“展示类型与打开行为均以真实类型为准”。
