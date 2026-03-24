# Tabs 行内关闭按钮优化 Spec

## Why
当前在 Tabs 页面关闭单个标签页依赖长按菜单，操作路径较长。用户希望在列表浏览时可直接点击每行末尾的关闭按钮，提升效率。

## What Changes
- 在 Tabs 列表每个标签项最右侧增加 `X` 关闭按钮（行内关闭）。
- 点击 `X` 后直接关闭对应标签，不影响其它标签。
- 从长按上下文菜单中移除 `Close` 项，避免重复入口。
- 保留长按菜单中的 `Close All` 与 `Add to Favorites`。

## Impact
- Affected specs: Tabs 列表交互、标签页关闭路径
- Affected code: `fragment_tabs_overview.xml`、`TabsOverviewFragment.kt`、可能新增 Tabs 行布局资源

## ADDED Requirements
### Requirement: Tabs 行内关闭
系统 SHALL 在 Tabs 列表每行末尾提供 `X` 关闭按钮，点击后立即关闭该行对应标签页。

#### Scenario: 点击行内 X 按钮
- **WHEN** 用户在 Tabs 页面点击某行末尾 `X`
- **THEN** 该标签页被关闭
- **AND** 列表立即刷新，其他标签页保持不变

## MODIFIED Requirements
### Requirement: Tabs 长按菜单结构
原有长按菜单中的 `Close` 项修改为移除；保留 `Close All`、`Add to Favorites`。

#### Scenario: 长按标签项
- **WHEN** 用户长按任意标签项
- **THEN** 菜单不再显示 `Close`
- **AND** 菜单仍显示 `Close All` 与 `Add to Favorites`
