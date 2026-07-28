# Changelog

## [1.1.0](https://github.com/f0reachARR/ModernVillagerShop/compare/v1.0.0...v1.1.0) (2026-07-28)


### Features

* **ui:** render item names as translatable components with item hover ([#6](https://github.com/f0reachARR/ModernVillagerShop/issues/6)) ([17da8c8](https://github.com/f0reachARR/ModernVillagerShop/commit/17da8c81a357ef704fd7ef0695ec457de225d4c5))

## 1.0.0 (2026-07-26)


### Features

* **action:** 名前変更/職業変更/削除ボタン追加・現在値表示・トグル後にDialog再表示 ([b87de07](https://github.com/f0reachARR/ModernVillagerShop/commit/b87de07e0a17ca726e913bde96e33a5938dbbe67))
* **api:** 公開API・Bukkit Events・PriceProvider SPI・PlaceholderAPI 拡張 ([e5f2b8a](https://github.com/f0reachARR/ModernVillagerShop/commit/e5f2b8a21d0faa4c5ebd812e7cace50b40351e0a))
* **chest:** チェストUIをショップのrowCountに追従させる ([b34f234](https://github.com/f0reachARR/ModernVillagerShop/commit/b34f234ec5b79d217d6a145d28797e4295783cfe))
* **command:** /vshop Brigadier コマンドを実装 ([1b5915a](https://github.com/f0reachARR/ModernVillagerShop/commit/1b5915a4a0c494d0078fba339731f819565cedc8))
* **command:** move /vshop help text into the locale files ([d1183f8](https://github.com/f0reachARR/ModernVillagerShop/commit/d1183f8c1152394f33640bb8d9fc746267a51403))
* **command:** move /vshop list output into the locale files ([7caa257](https://github.com/f0reachARR/ModernVillagerShop/commit/7caa257cf526034fedd7995df4a6f6ae32586ea0))
* **command:** share one localized stats renderer between chat and dialog ([9c9dbe8](https://github.com/f0reachARR/ModernVillagerShop/commit/9c9dbe83535ed867e3d9275d6c7bf06cf0c169a8))
* **command:** stats / search / history / migrate サブコマンドを追加 ([1f69d3a](https://github.com/f0reachARR/ModernVillagerShop/commit/1f69d3af28b9f507cfa62ec4a919a8ccc1391318))
* **coowner:** 共同オーナー管理 + PRIMARY 移譲 Dialog フロー ([ccbeb96](https://github.com/f0reachARR/ModernVillagerShop/commit/ccbeb961f4b5e5c0911e9882df6b29dc6d469770))
* **core:** プラグイン基盤・設定・i18nメッセージを整備 ([1bfd36b](https://github.com/f0reachARR/ModernVillagerShop/commit/1bfd36b0e1266a8d2a3cb4f73199366ea8e23777))
* **display:** 履歴/検索の表示を translatable item name + hoverEvent ベースに ([38698e9](https://github.com/f0reachARR/ModernVillagerShop/commit/38698e9bb63536349aab61e79ab422d2f92e5ecd))
* **edit:** ShopActionMenu のダイアログ経路で Esc/閉じ時に親メニューへ戻す ([7fff12f](https://github.com/f0reachARR/ModernVillagerShop/commit/7fff12fe796a1595448c0f1aaba86fef5af36239))
* **edit:** ShopActionMenu を情報/設定/オーナー管理のサブメニューに再編 ([8da3a7f](https://github.com/f0reachARR/ModernVillagerShop/commit/8da3a7f17d5e72800c9dc7769dd16f1772302e68))
* **edit:** スロット作成/編集/削除のチェストUI + Dialogフロー ([e74ec1b](https://github.com/f0reachARR/ModernVillagerShop/commit/e74ec1bfc070fd5176c8efdae372d70375617e01))
* **edit:** チェスト UI と CoOwnerFlow の Esc/閉じ経路で親メニューへ戻す ([a3e8c40](https://github.com/f0reachARR/ModernVillagerShop/commit/a3e8c40bf454b14662e9058828b92c7a96b4c320))
* **edit:** 出品枠編集をハブ+販売/納品フォームの2段構成に再編 ([f4d544f](https://github.com/f0reachARR/ModernVillagerShop/commit/f4d544fd1776db7141e9e141aff1c49626d4a843))
* **edit:** 在庫編集チェストで Shift+クリック一括移動を許可 ([a07362c](https://github.com/f0reachARR/ModernVillagerShop/commit/a07362c3c0eb7c236b2c5c9550eaf55ddce8c2bb))
* **history:** 取引履歴に相手プレイヤー名を表示・補完を文脈対応に改善 ([55b5230](https://github.com/f0reachARR/ModernVillagerShop/commit/55b523049d2b0ba38afbba12aa9053405e7efd33))
* **locale:** add EnumLabels for locale-backed enum display names ([0c6e03a](https://github.com/f0reachARR/ModernVillagerShop/commit/0c6e03a1dfda42b0b92d8ca9d7b0dd737fe8e406))
* **model:** ショップ・スロット・共同オーナー等のドメインモデルを追加 ([9b54709](https://github.com/f0reachARR/ModernVillagerShop/commit/9b547094294469d25b499f16dfcf3e23600953da))
* **price:** PriceProvider を取引・閲覧UIに統合し PriceSnapshot/乖離許容率を実装 ([1f4fa3e](https://github.com/f0reachARR/ModernVillagerShop/commit/1f4fa3eed92e11e2a006a5bf03be11e94b476338))
* **restock:** 在庫補充UI をページング対応にして実質無限容量に ([c2b1f4d](https://github.com/f0reachARR/ModernVillagerShop/commit/c2b1f4d05ec73125093a06ec886b60a0a6a25343))
* **shop:** スポーンエッグとショップVillager管理を実装 ([fa7c9fb](https://github.com/f0reachARR/ModernVillagerShop/commit/fa7c9fb5c55ace3ff01a7d164d61876eedf1f7cc))
* **shop:** 在庫がありREFUSEの場合はNoticeダイアログで通知 ([0efae88](https://github.com/f0reachARR/ModernVillagerShop/commit/0efae88774d738fd3f18ba77eec6d57694022135))
* **shop:** 在庫が残った状態での店舗閉店挙動を設定可能にする ([46d310d](https://github.com/f0reachARR/ModernVillagerShop/commit/46d310dd878f722f93cd5a684c5841c73b79310c))
* **slot:** 1パック上限を1スタックに制限しチェストUIに在庫数を表示 ([a629aee](https://github.com/f0reachARR/ModernVillagerShop/commit/a629aeec8d755a916e060125d1387d48b31b2788))
* **storage:** SQLite/MySQL個別実装のリポジトリ層を追加 ([449433f](https://github.com/f0reachARR/ModernVillagerShop/commit/449433ffebd783b32fd3c5990b673fb9bdc10321))
* **trade:** 1取引あたりの数量上限を 64 から 2304 (=インベントリ満載) に引き上げ ([81b3a05](https://github.com/f0reachARR/ModernVillagerShop/commit/81b3a059a28a3123355a3ddd9089cdff18b588a7))
* **trade:** Vault連携・SELL/BUY取引フロー・共同オーナー按分・オフライン通知 ([7881aee](https://github.com/f0reachARR/ModernVillagerShop/commit/7881aeeb42d0ec0f7c3a706ed743958b11ea23ab))
* **trade:** 取引上限残量も事前チェックして数量入力に進ませない ([a011fe0](https://github.com/f0reachARR/ModernVillagerShop/commit/a011fe078aed33ae6d1ccb89f99edb7e316ee96f))
* **trade:** 在庫/受入の事前チェックと買取受入数 -1=無制限を導入 ([e99ae4c](https://github.com/f0reachARR/ModernVillagerShop/commit/e99ae4cd13eb40f4347cda28e4151784b3221d0c))
* **trade:** 数量上限超過エラーに具体的な数値を載せる ([9555593](https://github.com/f0reachARR/ModernVillagerShop/commit/9555593090ac80953ccde54e2cb76d900cc9345e))
* **trade:** 納品時の手持ち不足を専用メッセージにし必要数と所持数を表示 ([2f66d48](https://github.com/f0reachARR/ModernVillagerShop/commit/2f66d4893b121ef2198e15fcb8e901e058914e39))
* **ui:** BedrockDialog ラッパー DialogService を追加 ([b8d6351](https://github.com/f0reachARR/ModernVillagerShop/commit/b8d6351655c77a3015ae23d816c1df726e8429a4))
* **ui:** チェスト UI とチャット出力を localize し価格に通貨単位を付与 ([54d9cda](https://github.com/f0reachARR/ModernVillagerShop/commit/54d9cda1e390608fab7bdfc05432ca05169e5606))
* **ui:** チェスト形式のショップ閲覧UIを追加 ([016640f](https://github.com/f0reachARR/ModernVillagerShop/commit/016640feac0d025ddf2f40b090c0d9701b6a098b))
* **ui:** プレイヤー選択チェストUI・在庫補充UI・編集アクションDialog を追加 ([4af8356](https://github.com/f0reachARR/ModernVillagerShop/commit/4af83563e565ff74dfb02ddcfa29c35dabca5108))


### Bug Fixes

* **compliance:** spec レビューで検出した非準拠点を一括修正 ([fd9a01c](https://github.com/f0reachARR/ModernVillagerShop/commit/fd9a01ca8bd2337c1c179f872127f8603afe5ce5))
* **config:** /vshop reload を各サービスに伝播させる ([2610d25](https://github.com/f0reachARR/ModernVillagerShop/commit/2610d257ee60b91b157f9de17deae1de70e65312))
* **coowner:** add Dialog で既存メンバーの role/share を既定値に表示 ([e480a2a](https://github.com/f0reachARR/ModernVillagerShop/commit/e480a2a975678f2229a9db96c3f2121aba343bf2))
* **coowner:** stop hardcoding PRIMARY/MANAGER in transfer and role messages ([2bcfefd](https://github.com/f0reachARR/ModernVillagerShop/commit/2bcfefd4d4f9ec84624c25c2cae2350c677fcd96))
* **edit:** プレイヤーインベントリ側のクリックをブロックしないよう修正 ([8e843b8](https://github.com/f0reachARR/ModernVillagerShop/commit/8e843b8cc521d3ace17d4a431adf696ca947e09c))
* **lang:** action.rename/profession/delete のドット入りキーをネスト構造に修正 ([946b840](https://github.com/f0reachARR/ModernVillagerShop/commit/946b840a4e0a5288fa7c6a4850dcb5c8be0b512b))
* **trade:** SQLite プール枯渇で取引がデッドロックする問題を解消 ([586c79a](https://github.com/f0reachARR/ModernVillagerShop/commit/586c79a1eaec046bc29bb06c73176fc7006ea469))
* **ui:** ADMINショップで在庫補充ボタンを非表示・実装と接続、購入UIのページボタンを必要時のみ表示 ([1fa16bf](https://github.com/f0reachARR/ModernVillagerShop/commit/1fa16bf93f145831681a450d9ccb82b090e14c50))
* **ui:** localize the suspended flag and drop the dead shop-info keys ([53eef98](https://github.com/f0reachARR/ModernVillagerShop/commit/53eef98325c7b0e5e28f91dd8670e7e4ce8ffe31))
* **villager:** 管理者ショップの Villager 名に空のブラケットを残さない ([3b5c80b](https://github.com/f0reachARR/ModernVillagerShop/commit/3b5c80b5b565feaa7cd9e9534f4200018236b884))


### Refactoring

* **command:** VShopCommand をサブコマンドごとに分割し /vshop search ページング・/vshop history フィルタを追加 ([c249dd0](https://github.com/f0reachARR/ModernVillagerShop/commit/c249dd09090d1179d1edb6a0941fd7beb75494af))
* **edit:** fold slot editor side/scope labels into the enum keys ([e2ef86f](https://github.com/f0reachARR/ModernVillagerShop/commit/e2ef86f56224412385987bad5d85ab443aa3185e))
* **edit:** ShopEditUi から在庫補充ボタンを削除 ([07ecb2d](https://github.com/f0reachARR/ModernVillagerShop/commit/07ecb2dec56eb4124b7b9cf1fb11295938e332b7))
* **ui:** render enum values through EnumLabels instead of name() ([5a67a60](https://github.com/f0reachARR/ModernVillagerShop/commit/5a67a60c3762b80390082ba3782c74ad9a73248a))


### Documentation

* add an English README and English guide translations ([4d30d5d](https://github.com/f0reachARR/ModernVillagerShop/commit/4d30d5da2147ca02cae29d7ac7ba8acbc0ab796a))
* add an English README and English guide translations ([e747661](https://github.com/f0reachARR/ModernVillagerShop/commit/e747661fcafdeed7eb8fb3a611d0d8f981c83c7a))
* **plugin:** paper-plugin.yml に権限一覧を定義 ([982d4dc](https://github.com/f0reachARR/ModernVillagerShop/commit/982d4dc092090ec5da535733f3e0ce9a0574f93d))
* record the EnumLabels convention for enum display names ([efa452d](https://github.com/f0reachARR/ModernVillagerShop/commit/efa452d73f58107946b0af2c53f4c45254e0f206))
* require the ja/en guide pair to stay in sync ([0b13571](https://github.com/f0reachARR/ModernVillagerShop/commit/0b135719e618e5ab161377670c13594c5d499f6f))
