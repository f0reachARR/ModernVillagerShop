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
  - BedrockDialog (Dialog API / Geyser Formラッパー)
    - 対応バージョン: Paper 1.21.8〜1.21.11、Java 21+
    - `paper-plugin.yml` の `dependencies.server` に `BedrockDialog` を `required: true` で宣言
    - Bedrock対応にはGeyser+Floodgateの併用を前提とする
  - Command API（Brigadier）
  - Adventure/MiniMessage

### 2.1 ビルド依存（Modrinth Maven）

- BedrockDialog は Modrinth Maven リポジトリから取得する。
  - リポジトリURL: `https://api.modrinth.com/maven`
  - groupId: `maven.modrinth`
  - artifactId: `bedrockdialog`
  - version: Modrinth 上の対応バージョン slug を指定（例: `bedrockdialog-x.y.z`）
- Gradle (Kotlin DSL) 例:

  ```kotlin
  repositories {
      exclusiveContent {
          forRepository {
              maven("https://api.modrinth.com/maven") { name = "modrinth" }
          }
          filter { includeGroup("maven.modrinth") }
      }
  }

  dependencies {
      compileOnly("maven.modrinth:bedrockdialog:<version>")
  }
  ```

- Maven (`pom.xml`) 例:

  ```xml
  <repositories>
    <repository>
      <id>modrinth</id>
      <url>https://api.modrinth.com/maven</url>
    </repository>
  </repositories>
  <dependencies>
    <dependency>
      <groupId>maven.modrinth</groupId>
      <artifactId>bedrockdialog</artifactId>
      <version><!-- 対応バージョン --></version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
  ```

- BedrockDialog はプラグイン依存として読み込ませるため、shade せず `compileOnly` / `provided` で参照する。

## 3. 主要機能

### 3.1 ショップ

- 共通
  - 専用スポーンエッグから作成
  - 出品枠はインベントリの1行（9スロット）単位で管理し、ショップごとに設定される
    - 行数は **1 以上の任意の正整数** または **無限（inf, 内部値 -1）**
    - **rowCount ≤ 6**: チェストUIを `rowCount * 9` スロットの単一ページで開く。ページネーション・ナビゲーション行なし（クローズは Escape）
    - **rowCount > 6**: 5 行のコンテンツ + 1 行のナビ行（計 54 スロット）で開き、45 スロット単位でページング。最終ページに `rowCount * 9` を超える余り枠が出る場合は灰のガラスパネルで埋めてクリック/配置を拒否する
    - **無限ショップ**: 5 行のコンテンツ + 1 行のナビ行で開き、45 スロット単位で無限ページング
  - 販売/買取/双方向の出品枠を持つ
    - SELL枠は「アイテム・単価・数量」
    - BUY枠は「アイテム・買取単価・上限数量」
    - 両方の枠を持つことも可能
  - ショップVillagerは AI無効化を常時適用し、移動を防止する
    - 本来存在するべき座標をDBに保存し、チャンクロード時に同UUIDのエンティティが存在するか検査し、無ければ再スポーンする
    - ワールド未ロード時は再スポーンを行わない
    - ネザーポータルなどでの移動はキャンセルする
  - ショップVillagerは invulnerable（無敵）に設定し、プレイヤー・モブ・環境ダメージから保護する
    - 削除はショップ削除フロー経由のみで行う
  - 配置制限
    - 既存ショップVillagerの座標から `shop.minDistance`（デフォルト: 0.5ブロック）未満の距離にはショップを配置できない
    - 設置時にスポーンエッグ使用を検証し、満たさない場合はエッグ消費せず拒否する
- プレイヤーショップ
  - 所有者（PRIMARY）付き。共同オーナーを設定可能（詳細は §3.6）
  - 在庫はショップ専用ストレージで保持し、論理上の容量制限は設けない（DB保持）
  - プレイヤーあたりの所有ショップ数は設定で上限を設定可能（デフォルト無制限）
    - カウント対象は PRIMARY ロールのみ。MANAGER / STAFF は対象外
  - ショップVillagerの表示名はショップ名と PRIMARY 名を含むフォーマットで表示する
    - フォーマットは `shop.villagerNameFormat` で設定可能（デフォルト: `<shop_name> <gray>[<primary>]</gray>`）
    - PRIMARY 変更時・ショップ改名時に CustomName を再生成する
- 管理者ショップ
  - 所有者なし（パーミッションを持つ者が編集可能）
  - 在庫は存在せず、出品枠ごとに無限在庫として扱う
  - 作成は `admin` タイプスポーンエッグから行う
- 公開状態
  - ショップは「公開 / 一時停止」状態を持つ
  - 一時停止中は購入・納品とも拒否し、利用者にその旨を案内する

### 3.2 販売（SELL）

- 店舗が在庫・単価を保持。
- 購入時に購入者から代金を引き落とし、手数料差引後の金額を販売者側へ即時入金。
  - 共同オーナーが設定されている場合は §3.6 の持分に従って按分入金する。
- 管理者ショップは販売者への入金先なし（システム処理）。

### 3.3 買取（BUY: 発注枠方式）

- 店主が「対象アイテム・買取単価・上限数量」を設定。
- 購入者（納品者）が条件一致アイテムを納品すると、店主が即時支払い。
- 納品されたアイテムはショップ在庫に加算され、販売に回せる。
- 管理者ショップでは納品アイテムはサーバが吸収（破棄）し、在庫には加算しない。
- 店主残高不足時の挙動
  - 取引直前に店主の所持金 < 買取単価 × 納品数 となる場合、取引を拒否する。
  - 納品者には「店主の残高不足」の旨を案内する。
  - 部分受領は行わない（金額・在庫整合を優先）。
  - 該当枠は自動停止せず、次回納品時に再度判定する。

### 3.4 取引の上限設定

- 出品枠ごとに取引上限数を設定可能（例: 64個）
- 上限は時間経過でリセットする設定も可能（例: 24時間ごと）
- 上限の開始は最初の取引発生時点とする
- 上限に達した場合は取引拒否し、リセットまで待つよう案内する
  - リセット周期が設定されている場合、閲覧・編集チェスト UI のスロット lore および上限到達時のエラーメッセージにリセットまでの残り時間を表示する
- 上限のカウントスコープは出品枠ごとに切り替え可能
  - `PER_PLAYER`: プレイヤー単位で集計
  - `GLOBAL`: 全プレイヤー合算（サーバー単位）で集計
  - デフォルトは設定で指定可能

### 3.5 アイテム同一性判定

- 同一性判定は `ItemStack#isSimilar` をベースとする（数量は無視、メタ・エンチャント・PDC等は一致を要求）
- 取引禁止アイテムは設定 `items.blacklist` でマテリアル名指定により列挙する
  - シュルカーボックス系・Bundle 等、中身を持ち得るアイテムは運用者の判断で blacklist に追加することを推奨する
  - プラグイン側での強制ブラックリストは持たない

### 3.6 共同オーナー（プレイヤーショップのみ）

- プレイヤーショップに対して、1名以上の共同オーナーを追加できる。管理者ショップは対象外。
- 各ショップにつき PRIMARY は常に 1 名のみ。MANAGER / STAFF は複数可。
- 役割と権限
  - `PRIMARY`: ショップ削除、共同オーナーの追加・削除・役割変更、PRIMARY 移譲、その他 MANAGER の全権限
  - `MANAGER`: 出品枠の追加・編集・削除、価格・数量変更、在庫補充/引出し、公開・一時停止切替、ショップ名・職業・配置（移動）の変更
  - `STAFF`: 在庫補充のみ（収益受取なし・編集権限なし）
- 収益分配
  - 各オーナーに持分（`share`、小数2桁の百分率）を割り当て、合計が 100.00 になるよう PRIMARY の持分で自動補正する。
  - STAFF の `share` は常に 0 として扱う。
  - SELL 取引成立時、手数料控除後の金額を `share` で按分し、対象オーナーの口座に即時入金する。
  - 端数（`economy.fractionDigits` 以下）は PRIMARY に集約する。
  - BUY 取引の支払い元は PRIMARY の所持金から行う（残高判定も PRIMARY を基準とする）。
- ショップ所有数カウント
  - `shop.maxShopsPerPlayer` のカウントは PRIMARY のみが対象。
  - MANAGER / STAFF への追加はカウントしない。
- PRIMARY 移譲
  - 現 PRIMARY は、既存の共同オーナー（MANAGER）または任意のプレイヤーへ PRIMARY を移譲できる。
  - 移譲確定時、譲渡元は MANAGER に自動降格する。
  - 移譲時に Villager の CustomName を再生成する。
- 通知の到達範囲
  - 取引通知: PRIMARY + MANAGER に配信。STAFF は配信なし。
  - 共同オーナーの追加・削除・役割変更・PRIMARY 移譲時、対象プレイヤーへ通知する（オフライン時はキュー）。
- 履歴・統計の閲覧
  - 全共同オーナーは自店舗の `/vshop history` / `/vshop stats` を閲覧可能。
- 既存ショップの移行
  - 既存の単独所有ショップは PRIMARY=旧オーナー / `share=100.00` で `shop_co_owners` に自動投入する。

## 4. コマンド仕様（Paper Command API）

- ルート: `/vshop`（引数なしの場合は `help` と同等の挙動）
- サブコマンド:
  - `/vshop help`: コマンド一覧と簡易ヘルプ表示
  - `/vshop open <shopId>`: ショップUIオープン（設定とパーミッションに従いオープン可否を判定。`open.any` > `open.<shopId>` > `open.nearby` の優先順位）
  - `/vshop stats <shopId>`: ショップ統計情報表示
  - `/vshop list [page]`: ショップ一覧・ページング対応
  - `/vshop search <item> [page]`: アイテム名・IDでショップ検索（ページング対応）
  - `/vshop edit <shopId>`: ショップ編集（PRIMARY / MANAGER / STAFF が役割範囲で実行可能。詳細は §3.6）
  - `/vshop coowner <shopId>`: 共同オーナー管理UIを開く（PRIMARY のみ）
  - `/vshop transfer <shopId> <player>`: PRIMARY 権限を譲渡する（PRIMARY のみ。確認 Dialog 経由）
  - `/vshop history [shopId] [page] [--side sell|buy] [--from <date>] [--to <date>] [--player <name>]`: 取引履歴表示（shopId省略時は自身が関与した履歴を表示）
  - `/vshop egg <player> <lines|"inf"|"admin">`: ショップスポーンエッグ配布
    - `lines`: 出品枠の行数（**1 以上の任意の正整数**、9スロット×行数の枠を持つショップになる。`lines ≤ 6` は単一ページ、`lines > 6` は 5 行/45 スロット単位でページング）
    - `"inf"`: 行数無制限ショップ用エッグ（45 スロット単位でページング）
    - `"admin"`: 管理者ショップ用エッグ（内部的には `inf` 相当の容量）
  - `/vshop migrate <from> <to>`: ストレージ間データマイグレーション（例: `sqlite -> mysql`）
  - `/vshop reload`: 設定・言語ファイル・メッセージキャッシュをリロードする（DB接続は再構築しない）
- 登録方式:
  - `LifecycleEvents.COMMANDS` で Brigadier ツリーを登録。
- 権限:
  - 共通方針
    - 自店舗で何ができるかは §3.6 のロール（PRIMARY/MANAGER/STAFF）で決まる。パーミッションはあくまで「コマンド/機能の利用可否」を制御する。
    - `*.others` 系は**管理者用**の上書きパーミッションで、当人がそのショップに対するロールを持っているかを問わず操作可能とする（運営・モデレーション用途）。
  - 基本操作
    - `modernvillagershop.use`: ショップUIを開く（購入・納品操作）ことを許可
    - `modernvillagershop.egg`: スポーンエッグの使用可否（"admin"タイプ以外）
  - 自ショップ編集（自身が PRIMARY または MANAGER のショップが対象）
    - `modernvillagershop.edit`: 自ショップの編集UIを開く
    - `modernvillagershop.edit.move`: 自ショップの移動
    - `modernvillagershop.edit.rename`: 自ショップの名称変更
    - `modernvillagershop.edit.profession`: 自ショップの職業変更
    - `modernvillagershop.edit.suspend`: 自ショップの公開・一時停止切り替え
    - `modernvillagershop.edit.delete`: 自ショップの削除（実行可能なのはロール上 PRIMARY のみ）
    - `modernvillagershop.edit.delete.refund`: ショップ削除時にスポーンエッグがドロップする
  - 共同オーナー（自ショップが対象、実行可能なのはロール上 PRIMARY のみ）
    - `modernvillagershop.coowner.manage`: 共同オーナーの追加・削除・役割変更・持分変更
    - `modernvillagershop.coowner.transfer`: PRIMARY 権限の移譲
  - 管理（運営向け・ロール非依存で任意ショップに介入可能）
    - `modernvillagershop.edit.others`: 他人のショップを編集（ロール無視）
    - `modernvillagershop.coowner.manage.others`: 任意のショップの共同オーナー管理（ロール無視）
    - `modernvillagershop.coowner.transfer.others`: 任意のショップの PRIMARY 強制移譲（離脱者対応など）
    - `modernvillagershop.history.others`: 他者・他ショップの取引履歴閲覧
    - `modernvillagershop.admin.egg`: "admin"タイプのスポーンエッグ使用可否
    - `modernvillagershop.admin.edit`: 管理者ショップの編集
  - 一覧・検索・履歴
    - `modernvillagershop.search`: ショップ検索
    - `modernvillagershop.stats`: 統計情報閲覧
    - `modernvillagershop.list`: ショップ一覧閲覧
    - `modernvillagershop.history`: 自身の取引履歴閲覧
  - 接続範囲
    - `modernvillagershop.open.nearby`: 近距離ショップのみオープン許可
    - `modernvillagershop.open.any`: 距離問わずショップオープン許可（nearbyより優先）
    - `modernvillagershop.open.<shopId>`: 特定ショップのオープン許可
  - その他
    - `modernvillagershop.migrate`: ストレージマイグレーション実行
    - `modernvillagershop.reload`

## 5. UI仕様（Dialog + チェストUI併用）

- Dialogフロー:
  - ショップ管理UI（取れるアクションの一覧表示）
  - ショップ初期化
  - 出品（SELL/BUY）登録・編集（**起点はチェストUI**、Dialog では枠の属性編集のみを担当。詳細はチェストUI利用フロー参照）
    - 種別（SELL / BUY / BOTH）選択
    - 対象アイテム確認
    - 価格・数量・取引上限・上限スコープ・リセット周期の入力
  - 価格/数量設定
  - 購入/納品の最終確認
  - 削除/職業変更/名称変更/公開・一時停止切り替えなどの編集フロー
  - 共同オーナー管理フロー（PRIMARY 専用、§3.6）
    - 一覧表示: メンバー名・役割・持分を列挙、項目選択で個別操作へ
    - 追加: プレイヤー選択チェストUI（後述）→ MultiButtonDialog で役割選択 → InputDialog で持分入力 → ConfirmDialog
    - 役割・持分変更: 同フローを既存メンバーに対して実行
    - 削除: ConfirmDialog
    - 持分合計は確定時に PRIMARY の持分で自動補正して 100.00 に整合化する
  - PRIMARY 移譲フロー（PRIMARY 専用）
    - プレイヤー選択チェストUI（後述）→ ConfirmDialog（譲渡元は自動的に MANAGER へ降格する旨を告知）
- 数量・価格入力UX
  - 数量・価格はすべて `InputDialog`（テキスト入力）で受け付ける。
  - Slider は UX 上扱いにくいため使用しない。
  - 入力値は整数として検証する（小数は §7 の通貨桁数設定に従う）。
- BedrockDialog 採用上の制約
  - 利用するダイアログ種別は `ConfirmDialog` / `NoticeDialog` / `MultiButtonDialog` / `InputDialog` に限る。
  - コールバックは非メインスレッドで発火する可能性があるため、Bukkit API 呼び出しは必ず `Bukkit.getScheduler().runTask(...)` でメインスレッドにディスパッチする。
  - Bedrock版では MiniMessage 装飾が plain text に落とされる前提で文言を設計する。
  - Bedrock版では `onClose` コールバック未サポートのため、「閉じる＝キャンセル」のロジックに依存せず、明示的なキャンセルボタンで代替する。
  - プログラム的なダイアログクローズは Floodgate 必須のため、強制クローズに依存しないフロー設計とする。
- 連打抑止
  - Confirm Dialog をオープン中の同一プレイヤーに対しては、同フローの再オープンを抑止し、多重決済を防止する。
- チェストUI利用フロー:
  - 商品一覧閲覧（閲覧モード）
    - 各スロットには出品枠のアイテムをサンプル表示し、Lore に種別（SELL/BUY/BOTH）・単価・在庫数（SELL）または受入残（BUY）・取引上限の残量・PriceProvider の `reason` を表示
    - 在庫切れ / 受入満杯のスロットはネガティブ表現（赤色ロア）で示す。表示は設定可能とする
    - スロット位置 = §8.1 `shop_slots.slot_index`
    - クリックでモード選択画面（BOTHの場合）→購入/納品の確認 Dialog へ遷移
  - 出品枠の登録・編集（編集モード、オーナー / MANAGER 専用、`/vshop edit` 起点）
    - 編集モード時は閲覧モードと別表現のチェストUIを開く（タイトルや枠飾りで識別）
    - 既存出品枠スロットをクリック → 出品枠編集 Dialog（種別・価格・数量・取引上限など）へ遷移
    - 空スロットにプレイヤーが自身のインベントリからアイテムを置く（ドロップ／シフトクリック等）と、対象アイテムをスナップショットしてチェストUIを閉じ、種別選択 → 価格・数量入力 Dialog → 確定で当該 `slot_index` に新規枠を登録するフローへ遷移する
      - 取り込んだアイテムは**プレイヤーのインベントリから消費しない**（参照用にコピーのみ保持する）
      - Dialog をキャンセル or 閉じた場合はスナップショットを破棄し、当該スロットは空のままとする
      - 取引禁止アイテム（§3.5）や数量0のスタックを置こうとした場合は、Dialog 起動前にエラー案内する
    - 枠削除は編集 Dialog 内の「削除」ボタン経由とし、誤操作防止のため ConfirmDialog を挟む
    - `slot_index` はフラットな整数として保存し、ページング後も一意性を保つ
      - `rowCount ≤ 6` のショップは単一ページのため `slot_index` ∈ `[0, rowCount * 9)` をそのまま保存
      - `rowCount > 6` または無限ショップは 45 スロット単位でページングし、`slot_index = page * 45 + slot` を保存。有限の場合は `slot_index < rowCount * 9` を満たすスロットのみが有効
    - 編集モード中は同ショップに対する購入/納品をブロックし、混在を防ぐ
  - ページング
  - ソート/フィルタ
  - プレイヤーショップの在庫補充・引き出し（オーナー専用）
    - 編集Dialogの「在庫を編集」ボタンからチェストUIへ遷移する
    - チェストUI内のスロットとプレイヤーインベントリ間でアイテムを自由に移動できる
    - スロット位置は §8.1 `shop_inventory.slot_index` として保存し、UI上の配置を維持する
    - 取引禁止アイテム（§3.5）はチェストUI閉鎖時の検証でプレイヤーへ返却する
    - チェストUIクローズ時に DB へ差分を反映し、進行中の取引と整合を取る
  - プレイヤー選択チェストUI
    - 用途: 共同オーナー追加、PRIMARY 移譲先選択、`/vshop history --player` 等のプレイヤー指定が必要なフロー全般
    - 表示: `PLAYER_HEAD` にプレイヤーのスキン（`SkullMeta#setOwningPlayer`）を設定し、表示名にプレイヤー名、Lore に最終ログイン日時を表示
    - 候補ソース: §8.1 `player_cache` に登録されたプレイヤーを表示（オンライン者を先頭に固定）
    - 並び順: デフォルトは最終ログイン降順。チェストUI内のソート切替アイコンで「名前昇順 / 最終ログイン降順」を切替
    - 検索: フィルタアイコン押下で InputDialog を開き、プレイヤー名の前方一致でキャッシュ全体を絞り込み
    - ページング: 既存のナビゲーションアイコンを利用
    - 候補は `player_cache` 登録済みプレイヤーに限定する。未参加プレイヤーは取扱対象外とする。
    - スキン取得は非同期で実行し、未取得時は `ui.chest.icons.unknownPlayer` で代替表示
    - Bedrock 版でも同チェストUIを使用する（プレイヤー頭の表示は Geyser のフォールバック挙動に従う）
- チェストUIのナビゲーション要素（次/前ページ・閉じる・ソート切替・フィルタ・戻る・プレイヤー頭デフォルト等）に使うアイテム表示は設定可能とする
  - マテリアル・カスタム名・ロア・カスタムモデルデータをキーごとに上書き可能
  - 例: `ui.chest.icons.nextPage`, `ui.chest.icons.prevPage`, `ui.chest.icons.close`, `ui.chest.icons.sort`, `ui.chest.icons.filter`, `ui.chest.icons.back`, `ui.chest.icons.unknownPlayer`, `ui.chest.icons.unavailable`, `ui.chest.icons.emptySlot`
- チャットでの出力(ページネーションあり)
  - 取引結果の要約
  - エラー・成功メッセージ
  - ショップ統計情報（`/vshop stats`）
    - ショップ基本情報（ID・名称・所有者・座標・公開状態・職業）
    - 出品枠数 / 稼働中枠数
    - 累計取引件数（SELL / BUY 別）
    - 累計売上額 / 累計買取支払額
    - 累計手数料
    - 直近 7 日間の取引件数推移
    - 人気アイテム Top 5（数量ベース）
  - 取引履歴の表示（`/vshop history`）
    - 列: 時刻・ショップ・取引種別・相手・アイテム・数量・単価・手数料
    - フィルタオプション: `--side sell|buy`, `--from <date>`, `--to <date>`, `--player <name>`
  - コマンドのヘルプや案内

## 6. スポーンエッグ方式

- スポーンエッグには識別情報（PDC）として行数種別などショップ生成に必要なパラメータを保持。
- 設置時は PDC の存在と内容妥当性のみを検証し、当該情報をもとにショップを生成する。
- 複製・クリエイティブでの再使用は許容する（運用上の利便性を優先）。
  - DBで一意性を担保する仕組みは設けない。
  - クライアントチートによる不正生成は本プラグインの責務外とし、別の対策レイヤー（権限・ログ・スタッフ運用）で対応する。

## 7. 経済・手数料

- 通貨はVault経由の単一主通貨。
- 取引手数料は設定可能な一律率（プレイヤーショップと管理者ショップで別々に指定可能）。
- 手数料は徴収後に消失させる（特定口座への振込は行わない）。
- 取引精算は即時。
- 売上の入金はオフラインのプレイヤーオーナーに対しても即時に行う（Vaultのオフライン入金APIを利用）。
- 失敗時は取引全体をロールバック（通貨・在庫・履歴整合を維持）。
- 価格・数量の境界
  - 単価の最小値・最大値、1出品枠あたりの最大数量を設定可能。
  - 0以下の単価は不可。
- 通貨の精度・丸め
  - 内部計算は `BigDecimal` を使用する。
  - 最小通貨単位は `economy.fractionDigits`（小数点以下桁数、デフォルト: `2`）で設定する。
  - 通貨ごとに最小単位が異なるため、絶対値ではなく桁数で表現する。
  - 丸めモードは `HALF_UP` を既定とし、`economy.roundingMode` で変更可能。
  - Vault Economy API は `double` ベースのため、入出金直前に `BigDecimal -> double` 変換を行う。変換後の値は丸め後の数値であることを保証する。
  - PriceProvider の `priceDriftTolerance` 比較も `BigDecimal` 同士で行う。
- 金額表示
  - 表示は Vault の `format()` を優先し、未提供時は `economy.fractionDigits` と通貨記号で整形する。
- 取引通知
  - プレイヤーショップのオーナーに対し、自身のショップで取引が発生したことを通知する。
    - オンライン時: 即時にチャット通知。
    - オフライン時: 次回ログイン時にまとめて通知（取引件数・合計金額を要約、詳細は `/vshop history` 案内）。
  - 通知の保留は §8.1 `shop_notifications` に保存し、配送後に既読化する。
  - 通知の有効/無効はプレイヤー単位で切り替え可能とする。設定値は §8.1 `player_preferences` に保存する。

## 8. 永続化（SQLite/MySQL両対応）

### 8.1 DB方針

- `storage.type: sqlite | mysql`
- 同時アクセス対策
  - 取引はトランザクション内で実行し、在庫・残数・残高をまとめて更新する。
  - SQLite はライタ直列化（書き込みは単一スレッド経由）で整合性を担保。
  - MySQL は行ロック（`SELECT ... FOR UPDATE`）+ `READ COMMITTED` 以上の分離レベルで実行。
- 主要エンティティ（概略）
  - `shops`: ショップID・種別（player/admin）・所有者UUID（PRIMARYのキャッシュ）・座標・職業・名称・公開状態
  - `shop_co_owners`: 共同オーナー（ショップID・プレイヤーUUID・役割 `PRIMARY|MANAGER|STAFF`・持分 DECIMAL(5,2)・追加日時・追加者UUID、`(shop_id, player_uuid)` を主キーとする）
    - `shops.owner_uuid` は本テーブルの PRIMARY 行とアプリケーション層で同期する
  - `shop_slots`: 出品枠（ショップID・slot_index・種別SELL/BUY/BOTH・アイテムBLOB・単価・数量上限・取引上限・上限スコープ・リセット周期）
    - `slot_index` はフラットな整数で、編集モードと閲覧モードで同じ座標表現を共有する
      - `rowCount ≤ 6` のショップ: `slot_index` ∈ `[0, rowCount * 9)`（単一ページ）
      - `rowCount > 6` または無限ショップ: `slot_index = page * 45 + slot`（45 スロット単位でページング。有限の場合は `slot_index < rowCount * 9` を満たすスロットのみが有効）
    - `(shop_id, slot_index)` を一意制約とする
  - `shop_inventory`: プレイヤーショップの在庫（ショップID・スロット位置・アイテムBLOB・数量）
    - `slot_index` は `shop_slots` と同じ規約（ショップの行数モードに従う）でチェストUIでの配置を維持する
  - `shop_limits`: 取引上限の使用量（出品枠・対象プレイヤー or グローバル・累積数量・期間開始時刻）
  - `shop_transactions`: 取引履歴（時刻・ショップID・出品枠・取引種別・買手UUID・売手UUID・アイテムBLOB・数量・単価・手数料・basePrice・finalPrice・resolvedBy）
  - `shop_notifications`: オフライン通知キュー（プレイヤーUUID・取引参照・要約用集計値・生成時刻・既読フラグ）
  - `player_preferences`: プレイヤー単位の設定（UUID・通知有効/無効・その他）
  - `player_cache`: プレイヤー選択UI用キャッシュ（UUID・name・name_lower・texture_value・texture_signature・texture_updated_at・last_seen・updated_at）
    - サーバー初回ログイン時・ログアウト時・名前変更検出時に upsert する
    - プレイヤーショップのオーナーや取引履歴の相手として登場した UUID も同テーブルに upsert し、未参加でも検索可能にする
    - スキンテクスチャは Bukkit/Paper の `PlayerProfile#getProperties()` 中の `textures` プロパティ（base64 エンコード値と署名）をそのまま保持する
    - 取得は非同期で行い、`texture_updated_at` が `playerCache.textureTtl`（デフォルト 7 日）を超えた場合に再取得する
    - 取得失敗時は既存値を維持し、初回未取得の場合は `ui.chest.icons.unknownPlayer` を代替表示する

### 8.2 マイグレーション

- ストレージ種別間のデータ移行機能を提供（`/vshop migrate <from> <to>`）。
- 移行中は取引を一時停止し、整合性を保つ。
- 移行はスキーマ初期化 → 全テーブルのバルクコピー → 整合性チェックの順で行う。
- 失敗時は移行先データをロールバックし、移行元はそのまま残す。

## 9. ローカライズ・メッセージ（MiniMessage）

### 9.1 言語ファイル

- `messages_ja.yml`
- `messages_en.yml`

### 9.2 設定

- `config.yml` の言語関連
  - `locale`（デフォルト: `ja_JP`）
  - `fallbackLocale`（デフォルト: `en_US`）
- 主要設定キー（言語以外、概略）
  - `storage.type`, `storage.mysql.*`
  - `economy.feeRate`, `economy.feeRateAdmin`, `economy.priceMin`, `economy.priceMax`, `economy.amountMax`, `economy.currencyFormat`
  - `economy.fractionDigits`, `economy.roundingMode`, `economy.priceDriftTolerance`
  - `economy.priceProvider.enabled`
  - `shop.maxShopsPerPlayer`, `shop.openDistance`, `shop.defaultLimitScope`, `shop.minDistance`
  - `shop.villagerNameFormat`（Villager 表示名のフォーマット、デフォルト: `<shop_name> <gray>[<primary>]</gray>`）
  - `playerCache.maxEntries`（プレイヤー選択UIで保持する上限件数、デフォルト: `5000`、超過時は `last_seen` 昇順で削除）
  - `playerCache.defaultSort`（`LAST_SEEN_DESC` | `NAME_ASC`、デフォルト: `LAST_SEEN_DESC`）
  - `playerCache.textureTtl`（スキンテクスチャの再取得周期、デフォルト: `7d`）
  - `items.blacklist`（マテリアル名リスト・容器系を含め運用者が定義）
  - `placeholderapi.enabled`

### 9.3 メッセージキー例

- `command.*`
- `dialog.*`
- `shop.*`
- `trade.*`
- `error.*`
- `system.*`

### 9.4 プレースホルダ例

- `<player>`, `<shop_id>`, `<shop_name>`, `<primary>`, `<role>`, `<share>`, `<item>`, `<amount>`, `<price>`, `<fee>`, `<world>`, `<x>`, `<y>`, `<z>`

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
- 取引上限判定（PER_PLAYER / GLOBAL 切り替え・期間リセット）
- アイテム同一性判定（isSimilar・blacklist判定）
- スポーンエッグのPDC読み取りとパラメータ復元
- 共同オーナーの持分按分計算（合計100%制約・端数のPRIMARY集約）

### 11.2 結合

- プレイヤーショップ作成 → 出品 → 購入 → 即時入金
- 買取枠作成 → 納品 → 即時支払い
- オフラインオーナーへの売上即時入金
- 共同オーナー設定下での SELL 売上按分入金
- PRIMARY 移譲（譲渡元の降格／離脱、Villager 名再生成、上限チェック）
- プレイヤー選択チェストUI（キャッシュ反映・検索フィルタ・未参加プレイヤー扱い）
- 出品枠登録・編集チェストUI（空スロット→新規登録、既存スロット→編集、`slot_index` のページング越え一意性、編集モード中の購入/納品ブロック）
- 共同オーナーの役割境界（STAFF が削除不可、MANAGER が共同オーナー管理不可、`*.others` 権限保有者のロール無視）
- 権限不足時のコマンド拒否
- 同時取引時の在庫負数防止
- 一時停止中ショップでの取引拒否
- ストレージマイグレーション（sqlite ⇔ mysql）の整合性
- SQLite/MySQLで同一挙動

### 11.3 受け入れ

- Dialog中心で主要フローが完結する。
- 一覧操作はチェストUIで直感的に行える。
- ロケール切替とfallbackが正しく機能する。

## 12. 拡張連携

### 12.1 PlaceholderAPI

- 任意依存。導入時に以下のプレースホルダを提供する。
  - `%mvshop_shop_count_<player>%`: プレイヤーの所有ショップ数
  - `%mvshop_shop_name_<shopId>%`: ショップ名
  - `%mvshop_shop_owner_<shopId>%`: 所有者名
  - `%mvshop_total_sales_<player>%`: プレイヤーの累計売上
  - `%mvshop_total_purchases_<player>%`: プレイヤーの累計購入額
  - 必要に応じて拡張可能な設計とする

### 12.2 公開API / イベント

- 他プラグインから利用できる Bukkit Event を提供する。
  - `ShopCreateEvent` / `ShopDeleteEvent`
  - `ShopPreTransactionEvent`（Cancellable・取引拒否専用。価格決定は §12.3 の Provider が担う）
  - `ShopTransactionEvent`（取引成立後）
  - `ShopSlotChangeEvent`（出品枠の追加・編集・削除）
- 公開APIファサードを提供する。
  - ショップ取得・検索・統計参照・取引履歴参照
  - サードパーティ統合（ログ、ダッシュボード等）を想定
- APIはセマンティックバージョニングに従い、互換性を維持する。

### 12.3 動的価格API（PriceProvider）

- 管理者ショップの価格を拡張プラグインから動的に決定するための SPI を提供する。
- パイプライン型 SPI として設計し、複数の Provider を順序付きで重ねがけできる。

#### 12.3.1 インターフェース

```java
public interface PriceProvider {
    String id();
    int order(); // 小さいほど先に適用。静的価格は内蔵 order=0

    PriceResult apply(PriceContext ctx, PriceResult previous);
}

public record PriceContext(
    Shop shop,
    ShopSlot slot,
    TradeSide side,             // SELL / BUY
    OfflinePlayer viewer,       // 表示・取引対象プレイヤー（null=非個別）
    int intendedAmount,         // 取引予定数量（表示時は 1）
    BigDecimal basePrice,       // 出品枠に設定された静的単価
    Instant at,
    TransactionHistoryView history, // 取引履歴アクセサ（累計・期間集計）
    Map<String, Object> attrs   // 拡張用の自由属性
) {}

public record PriceResult(
    @Nullable BigDecimal price,   // null は素通し（前段の結果を維持）
    @Nullable Component reason,   // 表示理由（例: "<green>セール中</green>"）
    Duration ttl                  // 表示キャッシュ寿命
) {}
```

- 登録: `ModernVillagerShopAPI.priceRegistry().register(plugin, provider)`
- 解決順: `order` 昇順に全 Provider を適用。各段は前段の `PriceResult` を受け、必要なら上書き／差分加算する。
- 適用範囲: v1 では管理者ショップのみ対象。プレイヤーショップでは Registry 側で Provider を呼び出さず、出品枠の静的単価を最終価格としてそのまま使用する。
- `reason` は表示時にチェストUIの Lore 末尾に追加表示される。
- 非常停止: `economy.priceProvider.enabled = false` で全 Provider を無効化し、管理者ショップも静的単価にフォールバックできる。

#### 12.3.2 取引整合性

- **PriceSnapshot 方式**: 購入確認 Dialog を開いた時点で価格を凍結し、確認後の決済まで同一値を使用する。確認画面に凍結時刻を表示する。
- **乖離許容率**: 決済直前に再解決した価格と凍結価格の乖離が設定閾値（`economy.priceDriftTolerance`）を超えた場合、取引を自動キャンセルし再表示を促す。
- **解決ログ**: 取引履歴に `basePrice`・`finalPrice`・`resolvedBy`（採用 Provider の id 列）を記録し、監査・問い合わせ対応に用いる。
- 取引拒否は `ShopPreTransactionEvent` 経由で行い、Provider は価格決定のみに責務を限定する。

#### 12.3.3 性能・安全性

- Provider は同期実行を前提とし、I/O ブロッキング処理は禁止する（規約）。
- `PriceResult#ttl` を尊重したキャッシュをショップ側に持ち、チェストUI描画時の連続再計算を抑制する。
- 例外時は当該 Provider をスキップして前段の結果を採用し、ログに警告を出す（取引は止めない）。

## 13. v1範囲外

- オークション機能
- 移動禁止の外力補正（テレポート/ノックバック完全封鎖）
- 売上保留金庫方式

## 14. 参照資料

- ローカル資料:
  - `adventure.md`
  - `dialog.md`
  - `modern-commands.md`
- 公式ドキュメント:
  - <https://docs.papermc.io/adventure/minimessage/>
  - <https://docs.papermc.io/paper/dev/dialogs/>
  - <https://docs.papermc.io/paper/dev/command-api/basics/introduction/>
