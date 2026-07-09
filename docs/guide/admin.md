# 管理者ガイド

サーバー運営者・OP 向けのガイドです。導入、設定、権限運用、管理者ショップの作り方、監査、マイグレーションを扱います。プレイヤー向け操作は [利用者ガイド（基本機能）](user-basic.md) と [利用者ガイド（発展機能）](user-advanced.md) を参照してください。

## 1. 導入と前提

### 1.1 動作環境

- **Paper 1.21.8 以降**（開発は 1.21.11 で動作確認）
- **Java 21**
- **Vault**: 必須。Vault 対応の Economy プラグイン（EssentialsX Economy など）が別途必要です。
- **BedrockDialog**: 必須。Modrinth 配布の Paper プラグイン。Bedrock 対応をしたい場合は Geyser + Floodgate も併せて導入します。
- **PlaceholderAPI**: 任意。導入すればプレースホルダーが利用できます。

### 1.2 インストール

1. `ModernVillagerShop-*.jar` を `plugins/` に配置します。
2. Vault、BedrockDialog を同じく配置します（PlaceholderAPI は必要に応じて）。
3. サーバーを起動すると `plugins/ModernVillagerShop/` 以下に `config.yml`、`lang/messages_ja.yml`、`lang/messages_en.yml` が展開されます。
4. Vault 対応 Economy プラグインが稼働していることを確認します。

## 2. 設定ファイル（`config.yml`）

主な項目を抜粋します。既定値は `src/main/resources/config.yml` を参照してください。

### 2.1 言語

```yaml
locale: ja_JP           # 既定ロケール
fallbackLocale: en_US   # 見つからないキー用のフォールバック
```

言語ファイルは `plugins/ModernVillagerShop/lang/messages_<locale>.yml` に配置します。キーが両言語で揃っている必要があります。

### 2.2 ストレージ

```yaml
storage:
  type: sqlite            # sqlite | mysql
  sqlite:
    file: shops.db        # プラグインデータフォルダ相対
  mysql:
    host: localhost
    port: 3306
    database: vshop
    username: root
    password: ""
    properties: "useUnicode=true&characterEncoding=utf8&useSSL=false"
    poolSize: 8
```

- SQLite はライタ直列化で整合性を担保。手軽ですがスケーラビリティは限定的です。
- MySQL は行ロック（`SELECT ... FOR UPDATE`）+ READ COMMITTED 以上での運用を想定。複数サーバー・大規模運用向け。
- 途中でバックエンドを切り替える場合は [8. マイグレーション](#8-マイグレーション) を参照してください。

### 2.3 経済・手数料

```yaml
economy:
  feeRate: 0.05           # プレイヤーショップ SELL の手数料率
  feeRateAdmin: 0.05      # 管理者ショップの手数料率
  priceMin: 1
  priceMax: 1000000
  amountMax: 2304
  fractionDigits: 2       # 小数桁数
  roundingMode: HALF_UP
  priceDriftTolerance: 0.01  # 価格凍結後の許容乖離率
  currencyFormat: "<amount> <currency>"
  priceProvider:
    enabled: true         # 動的価格 SPI 全体の ON/OFF
```

- 手数料は徴収後にサーバー上から消失させます（特定口座への振込は行いません）。
- `priceProvider.enabled: false` にすると、管理者ショップも静的単価にフォールバックします。障害切り分けや価格拡張プラグインの停止時に使用します。

### 2.4 ショップ

```yaml
shop:
  maxShopsPerPlayer: -1     # -1 で無制限
  openDistance: 6.0         # 右クリック開店の距離
  minDistance: 0.5          # 新設時に近すぎる既存ショップを弾く距離
  defaultLimitScope: PER_PLAYER
  villagerNameFormat: "<shop_name> <gray>[<primary>]</gray>"
  villagerNameFormatAdmin: "<shop_name>"
  closeWithInventory: REFUSE  # DISCARD | DROP | REFUSE
```

- `maxShopsPerPlayer` は **PRIMARY** ロールでの所有数のみをカウントします。共同オーナーとして参加している場合はカウントされません。
- `closeWithInventory` の挙動:
    - `DISCARD`: 在庫を破棄して削除
    - `DROP`: 在庫を店の位置にドロップして削除
    - `REFUSE`: 在庫があるうちは削除を拒否（既定・安全側）

### 2.5 プレイヤーキャッシュ

```yaml
playerCache:
  maxEntries: 5000            # 超えると last_seen 昇順で削除
  defaultSort: LAST_SEEN_DESC # LAST_SEEN_DESC | NAME_ASC
  textureTtl: 7d              # スキン再取得の有効期限
```

プレイヤー選択UI（共同オーナー追加、PRIMARY 移譲先、`--player` 指定など）で使うキャッシュです。ログイン時・ログアウト時・共同オーナー参照時にアップサートされます。

### 2.6 取引禁止アイテム

```yaml
items:
  blacklist:
    - SHULKER_BOX
    - WHITE_SHULKER_BOX
    # ... 各色シュルカー・BUNDLE 等
```

- Bukkit の Material 名で指定します。
- 既定でシュルカーボックス系とバンドルが入っています。内部を持てるアイテムは、想定外の複製・搾取経路になるため慎重に扱ってください。
- プラグイン側の強制ブラックリストはありません。運用ポリシーに応じて追加削除してください。

### 2.7 UI アイコン

`ui.chest.icons.*` で、チェストUI 内のナビゲーション用アイコン（次/前ページ、閉じる、絞り込み、並び替え、戻る、空スロット、利用不可、不明プレイヤーヘッド）のマテリアル・表示名・ロア・カスタムモデルデータをすべて上書きできます。テクスチャパック運用と組み合わせて外観を整えられます。

## 3. 権限運用

### 3.1 権限グループのまとめ

`paper-plugin.yml` に、次のロール的グルーピングが定義されています。LuckPerms などの権限プラグインで付与すると便利です。

- **`modernvillagershop.player`** (default: `true`): 一般プレイヤーが必要とする権限のパック。`use`, `egg`, `list`, `search`, `stats`, `history`, `open.nearby`, `edit.*`, `coowner.manage`, `coowner.transfer` を含む。
- **`modernvillagershop.admin`** (default: `op`): 管理者権限パック。`admin.egg`, `admin.edit`, `admin.export`, `admin.import`, `edit.others`, `coowner.manage.others`, `coowner.transfer.others`, `history.others`, `open.any`, `migrate`, `reload` を含む。

### 3.2 個別権限

代表的なもの:

| 権限 | 用途 |
| --- | --- |
| `modernvillagershop.use` | ショップUI を開く（購入・納品） |
| `modernvillagershop.egg` | プレイヤー用スポーンエッグの使用 |
| `modernvillagershop.admin.egg` | 管理者用スポーンエッグの使用 |
| `modernvillagershop.admin.edit` | 管理者ショップの編集 |
| `modernvillagershop.edit.*` | 自ショップの各種編集操作（move / rename / profession / suspend / delete / delete.refund） |
| `modernvillagershop.edit.others` | 他者ショップの編集（ロール無視） |
| `modernvillagershop.coowner.manage.others` | 任意ショップの共同オーナー管理 |
| `modernvillagershop.coowner.transfer.others` | 任意ショップの PRIMARY 強制移譲（離脱者対応など） |
| `modernvillagershop.history.others` | 他者・他ショップの取引履歴閲覧 |
| `modernvillagershop.open.nearby` / `open.any` / `open.<shopId>` | ショップUI を開ける距離条件。`open.any` が最優先。個別 shopId 指定も可 |
| `modernvillagershop.migrate` | ストレージマイグレーション |
| `modernvillagershop.reload` | 設定リロード |

### 3.3 「役割」と「権限」の関係

- **役割（PRIMARY / MANAGER / STAFF）** はショップごとに設定され、そのショップで**何ができるか**を決めます。
- **権限（`modernvillagershop.*`）** は **その機能を使えるかどうか** を決めます。
- 例: `modernvillagershop.edit.rename` を持たないプレイヤーは、たとえ PRIMARY でも自ショップの名前変更ができません。
- **`*.others`** は役割を無視して任意ショップに介入できる管理者向けのオーバーライドです。運営スタッフに限定して付与してください。

## 4. 管理者ショップの作成と運用

管理者ショップは「所有者なし・在庫無限」のショップで、公共販売所や NPC 交換所として使えます。

### 4.1 作成

1. `modernvillagershop.admin.egg` 権限を持ったユーザーで:

    ```
    /vshop egg <対象プレイヤー> admin
    ```

    `admin` タイプのスポーンエッグは内部的には `inf` 相当の容量（45 スロット単位ページング）です。
2. スポーンエッグを持って設置します。
3. 通常の編集フロー（`/vshop edit` / 村人右クリックで開いた `品目を編集`）で出品枠を登録します。管理者ショップの編集には `modernvillagershop.admin.edit` が必要です。

### 4.2 特徴

- **在庫は無限**: SELL は常に販売可能、BUY で納品されたアイテムはサーバに吸収（破棄）されます。
- **入金先なし**: SELL 取引の売上はシステム処理（誰にも振り込まれません）。
- **共同オーナーは設定不可**: `admin-shop` エラーが出ます。
- **手数料**: `economy.feeRateAdmin` を適用。プレイヤーショップと別々にチューニングできます。
- **動的価格対応**: `PriceProvider` SPI で価格を上書きできる唯一のショップ種別です（詳しくは [10. 動的価格 API](#10-動的価格-api-priceprovider)）。

### 4.3 スロットの一括入出力

管理者ショップの出品枠は YAML でエクスポート／インポートできます。既存の管理者ショップを別サーバーに複製したり、大量スロットを外部で編集したりする用途です。

対象の管理者ショップの村人を **視線先 8 ブロック以内** に捉えた状態で実行します。

```
/vshop admin export <ファイル名>
/vshop admin import <ファイル名>
```

- 権限: `modernvillagershop.admin.export` / `modernvillagershop.admin.import`
- ファイルは `plugins/ModernVillagerShop/exports/` 以下に配置されます。
- **export**: 既にファイルがある場合は上書きせずエラーになります。別名を指定するか既存ファイルを削除してください。
- **import**: 既存の全スロットを削除して置き換えます。実行前に自動バックアップが取られ、パスがメッセージで報告されます。

用途として、`export` した YAML を PR で管理してレビュー可能な形にしたり、ステージング→本番反映のワークフローに組み込めます。

## 5. コマンドリファレンス（管理者視点）

| コマンド | 説明 | 主な権限 |
| --- | --- | --- |
| `/vshop help` | ヘルプ表示 | — |
| `/vshop list [page]` | ショップ一覧 | `modernvillagershop.list` |
| `/vshop open <shopId>` | ショップUI を開く | `open.nearby` / `open.any` / `open.<shopId>` |
| `/vshop search <item> [page]` | アイテム名で検索 | `modernvillagershop.search` |
| `/vshop stats <shopId>` | 統計表示 | `modernvillagershop.stats` |
| `/vshop history [shopId] [page] [--flags]` | 取引履歴 | `history` / `history.others` |
| `/vshop edit [shopId]` | 編集メニュー | `edit` / `edit.others` |
| `/vshop coowner <shopId>` | 共同オーナー管理UI | `coowner.manage` / `.others` |
| `/vshop transfer <shopId> <player>` | PRIMARY 移譲 | `coowner.transfer` / `.others` |
| `/vshop egg <player> <lines\|inf\|admin>` | スポーンエッグ配布 | `egg` / `admin.egg` |
| `/vshop admin export <file>` | 管理者ショップスロット YAML 出力 | `admin.export` |
| `/vshop admin import <file>` | 管理者ショップスロット YAML 取込 | `admin.import` |
| `/vshop migrate <from> <to>` | ストレージマイグレーション | `migrate` |
| `/vshop reload` | 設定・言語・メッセージのリロード | `reload` |

`/vshop history` の `--from` / `--to` は `YYYY-MM-DD` または `YYYY-MM-DDTHH:mm[:ss]` を受け付け、サーバーのデフォルトタイムゾーンで解釈します。

## 6. 監査と取引ログ

- 取引履歴は DB に永続化され、他者・他ショップは `modernvillagershop.history.others` を持つユーザーが `/vshop history <shopId>` や `--player <name>` で閲覧できます。
- 各履歴レコードには `basePrice`（出品枠の静的単価）、`finalPrice`（実際の取引価格）、`resolvedBy`（採用された PriceProvider の id 列）が保存されます。動的価格の妥当性検証に使えます。
- ログ経由の分析より、DB に直接クエリを流した方が柔軟です。SQLite なら `shops.db`、MySQL なら設定した `database` に対して `shop_transactions` を SELECT してください。

## 7. リロード

```
/vshop reload
```

- `config.yml` と言語ファイルを再読み込みします。
- **DB 接続は再構築されません**。ストレージ設定を変えたい場合はサーバー再起動が必要です。
- ランタイム中に言語ファイルを差し替えたときの検証に便利です。

## 8. マイグレーション

SQLite ⇔ MySQL の間でデータを移行できます。

```
/vshop migrate <from> <to>
```

例: `/vshop migrate sqlite mysql`

- 権限: `modernvillagershop.migrate`
- **移行中は取引が一時停止されます。**
- 手順:
    1. スキーマ初期化（移行先）
    2. 全テーブルのバルクコピー
    3. 整合性チェック
- 失敗時は移行先データをロールバックし、移行元はそのまま残します。安全側に倒した設計です。

**推奨フロー**:

1. サーバーをメンテナンス告知して、実質的な取引を止める。
2. `config.yml` の `storage` を **移行先の設定に切り替える前に** バックアップ。
3. 移行先の MySQL に接続情報を確認（`config.yml` に反映）。
4. `/vshop migrate <from> <to>` を実行。
5. 完了メッセージを確認後、`config.yml` の `storage.type` を切り替え、サーバー再起動。
6. 動作確認後、旧データ（`shops.db` など）を破棄。

## 9. PlaceholderAPI 連携

`placeholderapi.enabled: true`（既定）で以下が使えます。

- `%mvshop_shop_count_<player>%`: プレイヤーの所有ショップ数（PRIMARY のみカウント）
- `%mvshop_shop_name_<shopId>%`: ショップ名
- `%mvshop_shop_owner_<shopId>%`: 所有者名
- `%mvshop_total_sales_<player>%`: プレイヤーの累計売上
- `%mvshop_total_purchases_<player>%`: プレイヤーの累計購入額

Scoreboard、Chat prefix、DeluxeMenus などに組み込めます。

## 10. 動的価格 API（PriceProvider）

他プラグインから、**管理者ショップ**の価格を動的に変更する SPI を提供しています（プレイヤーショップは対象外）。

### 10.1 基本設計

- パイプライン型: 複数の Provider を `order` 昇順で連鎖適用します。静的単価は内部で `order = 0` として扱われます。
- 各 Provider は `PriceContext` と前段の `PriceResult` を受け、価格・理由テキスト・キャッシュTTL を返します。
- `PriceResult#reason` はチェストUI の Lore 末尾に表示されます（Bedrock ではプレーンテキスト）。
- 取引成立時、`basePrice` / `finalPrice` / `resolvedBy`（適用された Provider id 列）が履歴に記録されます。

### 10.2 取引整合性

- **PriceSnapshot**: 購入確認ダイアログを開いた瞬間の価格に凍結。確定までその値を使います。
- **乖離許容率**: `economy.priceDriftTolerance` を超えて確定時の再解決値がズレた場合、取引を自動キャンセルします。
- 拒否ロジックは `ShopPreTransactionEvent` に集約。Provider は価格決定のみを担当します（Provider から取引を止めるのは非推奨）。

### 10.3 安全設計

- Provider は同期実行を前提とします。I/O ブロッキングを含めないでください（規約）。
- 例外時は当該 Provider をスキップし、前段の結果を採用（取引は止めません）。ログに警告を出します。
- `PriceResult#ttl` で描画キャッシュ寿命を指定してください。チェストUI の連続再計算を抑えられます。
- 全体停止スイッチ: `economy.priceProvider.enabled: false`。障害時のフェイルセーフに使えます。

詳細な interface は [spec.md §12.3](../../spec.md) を参照してください。

## 11. 拡張連携（イベント / API）

他プラグインから利用できる Bukkit Event を提供します。

- `ShopCreateEvent` / `ShopDeleteEvent`
- `ShopPreTransactionEvent`（Cancellable、取引拒否用）
- `ShopTransactionEvent`（成立後）
- `ShopSlotChangeEvent`（出品枠追加・編集・削除）

公開 API は `ServicesManager` 経由で取得できます:

```java
ModernVillagerShopAPI api = Bukkit.getServicesManager()
        .load(ModernVillagerShopAPI.class);
```

`api.priceRegistry().register(plugin, provider)` で PriceProvider を登録します。API はセマンティックバージョニングで互換性を維持します。

## 12. トラブルシューティング

| 症状 | 確認ポイント |
| --- | --- |
| 起動時にエラーで無効化される | Vault と BedrockDialog がロード済みか、Java 21 か、Paper 1.21.8+ か。 |
| Vault Economy が見つからない | Vault 対応の Economy プラグインが同居しているか。EssentialsX Economy などを入れる。 |
| Villager がショップにならない・消える | 該当チャンクがロードされているか。DB に保存されているので、チャンクロード時に UUID を検査して自動再スポーンする仕様（未ロード時は動きません）。 |
| MySQL 移行後に取引で不整合 | サーバー分離レベルが READ COMMITTED 以上か、`SELECT ... FOR UPDATE` を止める設定がないかを確認。 |
| Bedrock 版で装飾が消える | 仕様。BedrockDialog は MiniMessage 装飾をプレーンテキストに落とすため、文言側でも装飾に依存しない設計を推奨。 |
| ダイアログの `onClose` が動かない | Bedrock では未サポート。明示的なキャンセルボタン設計に依存する現行仕様通り。 |
| 取引通知が届かない | 対象プレイヤーの `player_preferences` で通知が OFF になっていないか。STAFF ロールは仕様上通知対象外。 |
| `/vshop egg` が admin タイプで拒否される | 実行者に `modernvillagershop.admin.egg` が付与されているか。 |
| `/vshop admin export` で「no-target-villager」 | 視線先 8 ブロック以内にショップ村人が入っていない。まっすぐ見てから実行。 |
| `/vshop migrate` 実行後も旧データを見ている | `config.yml` の `storage.type` を切り替えたうえでサーバー再起動していない可能性。 |

## 13. 参考資料

- [spec.md](../../spec.md): v1 仕様（本体設計）
- [dialog.md](../../dialog.md): Paper Dialog API + BedrockDialog 利用メモ
- [adventure.md](../../adventure.md): Adventure / MiniMessage 記法
- [modern-commands.md](../../modern-commands.md): Paper Brigadier コマンド API
- [利用者ガイド（基本機能）](user-basic.md) / [利用者ガイド（発展機能）](user-advanced.md)
