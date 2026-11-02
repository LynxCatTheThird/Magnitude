# 衍生源码与第三方通知

Magnitude 的部分尺寸几何与渲染接线衍生自 Pehkui／Pehkui Rebuilt。相关文件标记 MIT，保留原始版权通知；完整 MIT 文本位于安装包 `META-INF/licenses/Pehkui-Rebuilt-MIT.txt`。项目其余文件的许可没有因此自动改变。

来源仓库：`https://github.com/wuhenqiubai/Pehkui-Rebuilt`，26.3基线提交 `c3461c5f4fd6b74b042f79762f358950bb8e5beb`。原始版权：Copyright (c) 2019 - 2024 Virtuoel；Rebuilt及其他贡献者的署名保留于来源仓库。Magnitude修改：2026 Magnitude contributors。

| Magnitude 文件 | 上游路径（相对仓库）及改造 |
| --- | --- |
| ShapeDimensionsMixin | common/.../mixin/LivingEntityMixin.java 的getDimensions与 common/.../util/ScaleUtils.java 的getScaledDimensions；采用确认形状状态，原版base只乘一次 |
| ShapeRenderState、ShapeRenderStateMixin | common/.../util/PehkuiEntityRenderStateExtensions.java、fabric/.../mixin/client/EntityRenderStateMixin.java；只保留本增量实际使用的相对宽高 |
| ShapeRenderExtractMixin | common/.../mixin/client/EntityRendererMixin.java；从Magnitude确认状态提取渲染尺寸 |
| ShapeRenderSubmitMixin | common/.../mixin/client/EntityRenderManagerMixin.java；裁掉debug与平台分支，保留矩阵作用域和恢复，使用相对倍率 |

上表中的 `...` 表示 `src/main/java/virtuoel/pehkui`。这些实现作为Magnitude内部代码维护；产品不提供Pehkui兼容API或独立Pehkui模组。未复制研究样本、图标、配置框架或其他模组源码。
