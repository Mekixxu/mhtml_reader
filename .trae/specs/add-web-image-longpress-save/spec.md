# 网页长按图片保存 Spec

## Why
当前网页浏览页面无法直接保存图片，用户需要额外步骤才能下载资源。增加长按图片保存能力可显著提升图片（含 GIF）采集效率。

## What Changes
- 在网页浏览页面（WebView）中，长按图片或 GIF 时弹出“保存图片”选项。
- 点击“保存图片”后下载并保存到本地可访问目录，并给出成功/失败反馈。
- 兼容常见图片来源（http/https 与 data URL）；无法保存时给出明确提示。

## Impact
- Affected specs: Reader 页面 WebView 长按交互、媒体资源保存
- Affected code: `ReaderFragment`、WebView 长按处理、下载/文件保存工具、必要字符串资源

## ADDED Requirements
### Requirement: 长按图片出现保存选项
系统 SHALL 在网页浏览页面长按图片资源时提供“保存图片”操作入口。

#### Scenario: 长按普通图片或 GIF
- **WHEN** 用户在网页中长按图片（包括 gif）
- **THEN** 弹出包含“保存图片”的操作菜单

### Requirement: 图片保存执行
系统 SHALL 在用户确认“保存图片”后完成资源落盘并反馈结果。

#### Scenario: 保存成功
- **WHEN** 用户点击“保存图片”且资源可访问
- **THEN** 图片保存到本地并提示保存成功

#### Scenario: 保存失败
- **WHEN** 资源不可访问或写入失败
- **THEN** 显示失败提示且不导致页面崩溃

## MODIFIED Requirements
### Requirement: Reader 页面长按交互
原仅支持默认长按行为，修改为对图片命中类型增加保存动作入口；非图片区域保持现有行为。
