---
layout: post
title: 差分包
date: 2020-07-12 16:23:05
updated: 2020-07-12 16:23:05
tags:
  - apk
  - diff
  - patch
  - zip
categories: Android
---

调研 Android 差分包过程记录

<!-- More -->

## 前测

目前市场差分方案有

- bsdiff - 最常见的差分方案，例如国内应用市场的增量更新
- archive-patcher - 谷歌推出的基于 bsdiff 的优化方案，使差分包更小
- apkdiff - Github 开源的项目，基于 archive-patcher 思想
- xdelta - 市面使用较少，但比 bsdiff 性能佳

以两个大约 38M 的 apk 作为例子

- bsdiff：差分包 10M
- archive-patcher：得基于两个 zlib 包，所以得先将两个 apk 进行 zlib 压缩（体积可能会变大），但是差分效果佳，差分包 400k
- apkdiff：使用 lzma 压缩，差分包 300k，但是会导致合成包 md5 不一致，也可以先将 apk 进行 zlib 后再使用 apkdiff，则 md5 相同
- xdelta：差分包 10M，但是差分和合成的速度和内存优于 bsdiff

## 分析

### bsdiff

- 比较常见的差异算法，不过因为较通用，所以没有考虑 apk 实际上是一个 zip 压缩包的情况，直接将 apk 当成文件去进行差异对比，所以产生的差异包较大
- patch 时需要用到 O(m+n)的内存

### archive-patcher

- 严格基于 zlib，apk 需要使用 zlib 重新压缩
- 差分和合成依旧使用 bsdiff
- 之所以差分包大小比 bsdiff 小是因为先解压后再 diff，比直接 diff 更容易描述差异
- 如果 apk 没有 zlib，将直接 bsdiff（差异包大小会与 bsdiff 一样大）
- 将 apk 解压后进行 file-to-file 的差异对比，得到差异包
- patch 阶段将差异包压缩回 apk（使用了 zlib）得到最终 apk 包

### apkdiff

- 合成后只保证逻辑相同，不保证二进制相同，这一点可以通过 zlib 压缩后解决（因为解压和压缩用了相同的编码）
- 差分和合成使用 hdiff，性能优于 bsdiff
- 核心原理是将 apk 解压后进行差异对比，改动越小，差异包越小，比较符合增量更新意义
- 比 archive-patcher 暴力的是 diff 阶段直接解压然后差异对比，patch 阶段直接 zlib 压缩回 apk 包，所以会导致二进制不相同
- 所以提供了 ApkNormalized 预处理 apk 包
- 需要注意的是，ApkNormalized 重新压缩 apk 包后导致的二进制不相同会影响 v2 和 v3 的签名（v1 不影响是 v1 不严格要求），所以如果项目使用 v2 或 v3 的签名方式需要在 ApkNormalized 后进行重签名

### xdelta

- 性能比 bsdiff 好以及稳定，但差异包大小与 bsdiff 差不多甚至更大一些
- 没有继续研究

## 接入成本

- 以 armeabi-v7a 为例
- jni 以 so 实际大小
- java 以 jar 实际大小

|          | bsdiff | archive-patcher | apkdiff | xdelta |
| -------- | ------ | --------------- | ------- | ------ |
| 接入方式 | jni    | java            | jni     | jni    |
| 接入大小 | 322KB  | 190KB           | 137KB   |        |

## diff 对比

- 由于 diff 过程属于预处理，一般放在服务器，所以只做时间对比
- 设备信息如下
- - macOs Catalina 10.15.4
- - 3.1GHz i5 16GB

|                | bsdiff   | archive-patcher | apkdiff  | xdelta  |
| -------------- | -------- | --------------- | -------- | ------- |
| 差分包生成时间 | 42s 左右 | 24s 左右        | 10s 左右 | 4s 左右 |

## patch 对比

- 测试机信息如下
- - 华为 LRA-AL00 Android9 API28
- - 运行内存 8.0GB
- - 屏幕 2400 x 1080
- - 手机存储 128 GB
- - CPU 架构 arm64-v8a

|            | bsdiff  | archive-patcher | apkdiff | xdelta |
| ---------- | ------- | --------------- | ------- | ------ |
| 差分包大小 | 10.6M   | 448k            | 304k    |        |
| cpu 占用   | 17%-24% | 17%-22%         | 17%-23% |        |
| 内存占用   | 86M     | 80M             | 6M      |        |
| 合成时间   | 5104ms  | 3841ms          | 3140ms  |        |

> 实际做了挺多次对比，结果大致相同，所以不放出

## patch 时多渠道因素

### 问题

前因

- 差异包只会打一个，以官方渠道包的新旧两个 apk 进行差异对比得到的差异包
- 线上会存在多种渠道包，目前渠道包以两种形式存在
- 1、ng-plugin 生成的渠道包（在 Zip Comment Central Directory 区域实现）
- 2、Android Manifest 中 meta-data（部分渠道包还会更改应用名）

后果

- 由于 diff&patch 要求 oldApk 完全一致（渠道包因为 Android Manifest 文件不同已经破坏一致），线上用户使用本地渠道包进行 patch 违反这一条约，所以 patch 会失败

### 如何解决 Zip Comment Central Directory

ng-plugin 方案是在 Central Directory 的注释部分插入渠道信息，不会到 apk 造成破坏，可以正常安装，但注释也是数据的一部分，也会参与 diff，所以会造成渠道包和原始包不一致导致无法 patch

- 通过先抹除渠道信息再 patch
- 如果需要保留原渠道信息，还可以再进行渠道的插入

### 如何解决 Android Manifest

思路 1

- 用户在拿到 patch 包后
- 利用本地渠道包还原 oldApk，再以 oldApk 去进行 patch 得到 newApk
- 难点 1、还原 oldApk 过程需要与 Jenkins 打包流程一致才能保证 100%还原
- 不足点 1、还原所需时间=打包所需时间，用户端消耗不起

思路 2

- diff 阶段排除 Android Manifest 文件的差异对比
- 下发 patch 包同时下发新的 Android Manifest 文件
- patch 阶段将新 Android Manifest 文件 copy 进去后再压缩回 apk 文件
- 难点 1、保证生成的 newApk 与正常流程的 newApk 一致

## Demo

[https://github.com/izyhang/DiffPatchRecord](https://github.com/izyhang/DiffPatchRecord)
