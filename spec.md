# ModernVillagerShop v1 仕様

## 1. 概要
- 本プラグインは、Paper 1.21.8+ 向けの Villager ベースショップシステムを提供する。
- ショップは販売（SELL）だけでなく買取（BUY: 発注枠方式）にも対応する。
- プレイヤーショップはコマンドで取得する専用スポーンエッグ経由で作成する。
- UIは Paper Dialog API を中心に使用し、アイテム一覧などチェストUIが適する場面のみチェストUIを併用する。
- テキスト表示は Adventure/MiniMessage ベースのローカライズYAMLで管理する。
- コマンド実装は Paper Command API（Brigadier）を使用する。

## 2. 対象環境・依存
- Minecraft/Paper: 1.21.8+
- Java: 21
- Economy: Vault 必須
- API利用:
  - Dialog API（Experimental）
  - Command API（Brigadier）
  - Adventure/MiniMessage

## 3. 主要機能
### 3.1 ショップ種別
- プレイヤーショップ
  - 所有者付き
  - 専用スポーンエッグから作成
  - 販売/買取の出品枠を持つ
- 管理者ショップ
  - 所有者なし（または管理者管理）
  - 販売/買取のカタログを持つ

### 3.2 販売（SELL）
- 店舗が在庫・単価を保持。
- 購入時に購入者から代金を引き落とし、手数料差引後の金額を販売者へ即時入金。
- 管理者ショップは販売者への入金先なし（システム処理）。

### 3.3 買取（BUY: 発注枠方式）
- 店主が「対象アイテム・買取単価・上限数量」を設定。
- 購入者（納品者）が条件一致アイテムを納品すると、店主が即時支払い。
- 納品されたアイテムはショップ在庫に加算され、販売に回せる。

### 3.4 アイテム種類上限
- 1ショップ内の取扱種類数を制限する。
- 判定は SELL + BUY の合算。
- 上限超過時は新規登録を拒否し、ローカライズメッセージを表示する。

### 3.5 ショップ移動禁止
- ショップVillagerは AI無効化を常時適用する。
- ワールド座標はDBに保存し、起動時にAI無効状態を再適用する。
- v1では外力による座標変化の自動補正は対象外。

## 4. コマンド仕様（Paper Command API）
- ルート: `/vshop`
- サブコマンド:
  - `/vshop open`
  - `/vshop egg`
  - `/vshop admin create`
  - `/vshop admin edit <shopId>`
  - `/vshop reload`
- 登録方式:
  - `LifecycleEvents.COMMANDS` で Brigadier ツリーを登録。
- 権限:
  - `modernvillagershop.use`
  - `modernvillagershop.create`
  - `modernvillagershop.admin`
  - `modernvillagershop.reload`

## 5. UI仕様（Dialog中心 + チェストUI併用）
- Dialog API 必須フロー:
  - ショップ初期化
  - 出品（SELL/BUY）登録・編集
  - 価格/数量設定
  - 購入/納品の最終確認
  - 削除など破壊的操作の確認
- チェストUI利用フロー:
  - 商品一覧閲覧
  - ページング
  - ソート/フィルタ

## 6. スポーンエッグ方式
- `/vshop egg` で専用スポーンエッグを配布。
- スポーンエッグには識別情報（PDC/NBT）を保持。
- 設置時に真正性を検証し、正当なもののみショップ候補として処理。

## 7. 経済・手数料
- 通貨はVault経由の単一主通貨。
- 取引手数料は設定可能な一律率。
- 取引精算は即時。
- 失敗時は取引全体をロールバック（通貨・在庫・履歴整合を維持）。

## 8. 永続化（SQLite/MySQL両対応）
### 8.1 DB方針
- `storage.type: sqlite | mysql`
- 同一スキーマで両方をサポート。

### 8.2 論理モデル
- `shops`
  - shopId, type, villagerUuid, ownerUuid, world, x, y, z, active, createdAt, updatedAt
- `listings`
  - listingId, shopId, mode(SELL|BUY), itemSerialized, unitPrice, stock/targetStock, enabled, updatedAt
- `transactions`
  - txId, shopId, listingId, direction(PURCHASE|PROCUREMENT), buyerUuid, sellerUuid, qty, gross, fee, net, createdAt

## 9. ローカライズ・メッセージ（MiniMessage）
### 9.1 言語ファイル
- `messages_ja.yml`
- `messages_en.yml`

### 9.2 設定
- `config.yml`
  - `locale`（デフォルト: `ja_JP`）
  - `fallbackLocale`（デフォルト: `en_US`）

### 9.3 メッセージキー例
- `command.*`
- `dialog.*`
- `shop.*`
- `trade.*`
- `error.*`
- `system.*`

### 9.4 プレースホルダ例
- `<player>`, `<shop_id>`, `<item>`, `<amount>`, `<price>`, `<fee>`, `<world>`, `<x>`, `<y>`, `<z>`

### 9.5 解決順
1. 選択ロケールのキー
2. fallbackロケールのキー
3. 最終固定フォールバック文言

## 10. 非機能要件
- 取引整合性優先（在庫・通貨・履歴の不整合を許容しない）。
- 主要操作はプレイヤーに分かりやすい文言で通知。
- 例外時もサーバークラッシュを避け、安全に失敗させる。

## 11. テスト観点
### 11.1 単体
- 手数料計算（境界値）
- 種類上限判定（SELL+BUY合算）
- スポーンエッグ真正性判定

### 11.2 結合
- プレイヤーショップ作成 → 出品 → 購入 → 即時入金
- 買取枠作成 → 納品 → 即時支払い
- 権限不足時のコマンド拒否
- 同時取引時の在庫負数防止
- SQLite/MySQLで同一挙動

### 11.3 受け入れ
- Dialog中心で主要フローが完結する。
- 一覧操作はチェストUIで直感的に行える。
- ロケール切替とfallbackが正しく機能する。

## 12. v1範囲外
- オークション機能
- 移動禁止の外力補正（テレポート/ノックバック完全封鎖）
- 売上保留金庫方式

## 13. 参照資料
- ローカル資料:
  - `adventure.md`
  - `dialog.md`
  - `modern-commands.md`
- 公式ドキュメント:
  - https://docs.papermc.io/adventure/minimessage/
  - https://docs.papermc.io/paper/dev/dialogs/
  - https://docs.papermc.io/paper/dev/command-api/basics/introduction/
