# PaperMC Dialog API リファレンス

> **対象バージョン**: Paper 1.21.7 以降（Minecraft 1.21.6 で追加された Dialog 機能の Paper API ラッパー）
> **APIステータス**: Experimental（将来変更される可能性あり）
> **情報源**: https://docs.papermc.io/paper/dev/dialogs/ / PaperMC Javadoc

---

## 1. 概要

Dialog（ダイアログ）は Minecraft 1.21.6 で追加されたゲーム内メニュー機能であり、サーバーからクライアントにカスタムUIを送信できる。Paper は 1.21.7 でこの機能に対する開発者向けAPIを提供開始した。

ダイアログは **コンフィギュレーションフェーズ**（プレイヤー接続時）と**通常のゲームプレイ中**の両方で表示可能なため、ルール同意画面、入力フォーム、情報表示など幅広い用途に使える。

---

## 2. パッケージ構成

```
io.papermc.paper.dialog
├── Dialog                          // ダイアログ本体のインターフェース

io.papermc.paper.registry.data.dialog
├── DialogBase                      // ダイアログの基本設定（タイトル、ボディ、入力等）
├── DialogBase.Builder              // DialogBase のビルダー
├── DialogBase.DialogAfterAction    // ダイアログ閉じた後のアクション（enum）
├── ActionButton                    // アクションボタン
├── ActionButton.Builder            // ActionButton のビルダー
├── DialogRegistryEntry             // レジストリエントリ
├── DialogRegistryEntry.Builder     // レジストリエントリのビルダー

io.papermc.paper.registry.data.dialog.body
├── DialogBody                      // ダイアログ本文（テキストやアイテム表示）

io.papermc.paper.registry.data.dialog.input
├── DialogInput                     // ユーザー入力（bool, text, singleOption, numberRange）

io.papermc.paper.registry.data.dialog.type
├── DialogType                      // ダイアログ種別（sealed interface）
├── NoticeType                      // 通知型
├── ConfirmationType                // 確認型（Yes/No）
├── DialogListType                  // ダイアログリスト型
├── MultiActionType                 // 複数アクション型
├── ServerLinksType                 // サーバーリンク型

io.papermc.paper.registry.data.dialog.action
├── DialogAction                    // アクション定義（sealed interface）
├── DialogAction.CommandTemplateAction  // コマンドテンプレート実行
├── DialogAction.CustomClickAction      // カスタムクリックアクション
├── DialogAction.StaticAction           // 静的クリックイベント
├── DialogActionCallback                // コールバック関数インターフェース

io.papermc.paper.dialog
├── DialogResponseView              // ダイアログ入力値の読み取りビュー

io.papermc.paper.registry.keys
├── DialogKeys                      // 組み込みダイアログのキー定数
```

---

## 3. ダイアログの表示方法

### 3.1 コマンドによる表示

```
/dialog show <players> <dialog>
```

### 3.2 API による表示

```java
// Audience#showDialog を使用（Adventure API）
player.showDialog(dialog);

// PlayerConfigurationConnection 経由でも可能（コンフィギュレーションフェーズ）
connection.getAudience().showDialog(dialog);
```

### 3.3 ダイアログの閉じ方

```java
// 推奨: ダイアログのみ閉じる（開いているインベントリは維持される）
audience.closeDialog();

// 代替: ダイアログ含む全画面を閉じる（インベントリも閉じる）
player.closeInventory();
```

---

## 4. ダイアログの作成

ダイアログには必ず **base（基本設定）** と **type（種別）** が必要。

### 4.1 動的作成（`Dialog.create`）

通常のサーバー稼働中に作成する方法。

```java
Dialog dialog = Dialog.create(builder -> builder.empty()
    .base(DialogBase.builder(Component.text("タイトル"))
        // オプション設定...
        .build()
    )
    .type(DialogType.notice())
);

player.showDialog(dialog);
```

### 4.2 レジストリ登録（ブートストラップフェーズ）

プラグインのブートストラッパーでレジストリに登録する方法。コマンドからの参照やコード内での再利用に便利。

```java
// PluginBootstrapper 内
@Override
public void bootstrap(BootstrapContext context) {
    context.getLifecycleManager().registerEventHandler(
        RegistryEvents.DIALOG.compose()
            .newHandler(event -> event.registry().register(
                DialogKeys.create(Key.key("myplugin:my_dialog")),
                builder -> builder
                    .base(DialogBase.builder(Component.text("タイトル")).build())
                    .type(DialogType.notice())
            ))
    );
}
```

### 4.3 レジストリからの取得

```java
Dialog dialog = RegistryAccess.registryAccess()
    .getRegistry(RegistryKey.DIALOG)
    .get(Key.key("myplugin:my_dialog"));
```

---

## 5. DialogBase（ダイアログ基本設定）

`DialogBase.builder(Component title)` で作成開始。

### 5.1 ビルダーメソッド一覧

| メソッド | 型 | 説明 |
|---|---|---|
| `canCloseWithEscape(boolean)` | `boolean` | Escキーで閉じられるか（デフォルト: true） |
| `externalTitle(Component)` | `Component` or `null` | このダイアログを開くボタンに表示されるタイトル |
| `afterAction(DialogAfterAction)` | `DialogAfterAction` | ダイアログ閉じた後のアクション |
| `pause(boolean)` | `boolean` | シングルプレイ時にゲームを一時停止するか |
| `body(List<? extends DialogBody>)` | `List<DialogBody>` | ダイアログ本文（テキスト・アイテム） |
| `inputs(List<? extends DialogInput>)` | `List<DialogInput>` | 入力フィールド一覧 |

### 5.2 使用例

```java
DialogBase.builder(Component.text("ルールへの同意", NamedTextColor.LIGHT_PURPLE))
    .canCloseWithEscape(false)
    .body(List.of(
        DialogBody.plainMessage(Component.text("サーバールールに同意しますか？")),
        DialogBody.plainMessage(Component.text("詳細はWebサイトをご確認ください。"))
    ))
    .inputs(List.of(
        DialogInput.text("name", Component.text("お名前")),
        DialogInput.bool("agree", Component.text("同意する"))
    ))
    .build()
```

---

## 6. DialogBody（ダイアログ本文）

ダイアログ内にコンテンツを表示するための要素。複数指定可能。

| ファクトリメソッド | 説明 |
|---|---|
| `DialogBody.plainMessage(Component)` | テキストメッセージを表示 |
| `DialogBody.item(ItemStack)` | アイテムを表示 |

---

## 7. DialogInput（入力フィールド）

ユーザーからの入力を受け取るためのフィールド。各入力は `key`（文字列キー）で識別される。

### 7.1 入力タイプ一覧

| ファクトリメソッド | 説明 | UIイメージ |
|---|---|---|
| `DialogInput.bool(String key, Component label)` | チェックボックス（true/false） | トグルスイッチ |
| `DialogInput.text(String key, Component label)` | テキスト入力フィールド | 文字列入力欄 |
| `DialogInput.singleOption(String key, Component label, List<...> options)` | 択一選択ボタン | 複数ボタンから1つ選択 |
| `DialogInput.numberRange(String key, Component label, float min, float max)` | 数値スライダー | スライダーUI |

### 7.2 numberRange の追加設定

`DialogInput.numberRange()` は追加のビルダーメソッドを持つ:

```java
DialogInput.numberRange("level", Component.text("レベル"), 0f, 100f)
    .step(1f)          // ステップ値
    .initial(0f)       // 初期値
    .width(300)        // 表示幅
    .labelFormat("%s: %s")  // ラベルフォーマット
    .build()
```

### 7.3 入力値の読み取り

入力値は `DialogResponseView` 経由で取得する:

```java
DialogResponseView view = event.getDialogResponseView();
if (view != null) {
    // numberRange の値取得
    int level = view.getFloat("level").intValue();
    float exp = view.getFloat("experience").floatValue();

    // text の値取得（キーを指定）
    // bool の値取得（キーを指定）
}
```

---

## 8. DialogType（ダイアログ種別）

`DialogType` は sealed interface で、ダイアログ下部のボタン構成を決定する。

### 8.1 種別一覧

| 種別 | ファクトリメソッド | 説明 |
|---|---|---|
| **Notice** | `DialogType.notice()` | OKボタンのみ。情報表示用 |
| **Notice** (カスタム) | `DialogType.notice(ActionButton button)` | カスタムボタン1つ |
| **Confirmation** | `DialogType.confirmation(ActionButton yes, ActionButton no)` | Yes/Noの2ボタン |
| **Multi Action** | `DialogType.multiAction(List<ActionButton> actions)` | 複数ボタン |
| **Dialog List** | `DialogType.dialogList(RegistrySet<Dialog> dialogs)` | 他のダイアログへのリンク一覧 |
| **Server Links** | `DialogType.serverLinks(ActionButton exit, int columns, int buttonWidth)` | サーバーリンク表示 |

### 8.2 使用例

```java
// Notice（シンプル）
DialogType.notice()

// Confirmation
DialogType.confirmation(
    ActionButton.builder(Component.text("同意する", TextColor.color(0xAEFFC1)))
        .tooltip(Component.text("クリックして同意"))
        .action(DialogAction.customClick(Key.key("myplugin:agree"), null))
        .build(),
    ActionButton.builder(Component.text("拒否する", TextColor.color(0xFFA0B1)))
        .tooltip(Component.text("クリックして拒否"))
        .action(DialogAction.customClick(Key.key("myplugin:disagree"), null))
        .build()
)

// Multi Action
DialogType.multiAction(List.of(
    ActionButton.create(Component.text("選択肢A"), null, 100, someAction),
    ActionButton.create(Component.text("選択肢B"), null, 100, anotherAction),
    ActionButton.create(Component.text("選択肢C"), null, 100, yetAnotherAction)
))
```

---

## 9. ActionButton（アクションボタン）

### 9.1 作成方法

```java
// ビルダーパターン
ActionButton.builder(Component.text("ラベル"))
    .tooltip(Component.text("ツールチップ"))
    .action(dialogAction)  // DialogAction or null
    .build()

// ファクトリメソッド
ActionButton.create(
    Component.text("ラベル"),       // label
    Component.text("ツールチップ"), // tooltip (nullable)
    100,                           // width (1-1024)
    dialogAction                   // action (nullable; null = 閉じるのみ)
)
```

### 9.2 action が null の場合

ボタンの `action` を `null` にすると、クリック時にダイアログを閉じるだけで他の処理は行われない。

---

## 10. DialogAction（アクション定義）

`DialogAction` は sealed interface で3つの実装を持つ。

### 10.1 CustomClickAction

サーバーに `PlayerCustomClickEvent` を送信する。最も汎用的。

```java
// Key + NBTペイロード方式（イベントリスナーで処理）
DialogAction.customClick(Key.key("myplugin:action_id"), null)

// コールバック方式（ラムダで直接処理）
DialogAction.customClick(
    (DialogResponseView view, Audience audience) -> {
        // ここで入力値を処理
        if (audience instanceof Player player) {
            // プレイヤーへの処理
        }
    },
    ClickCallback.Options.builder()
        .uses(1)                              // 使用回数（デフォルト: 1）
        .lifetime(ClickCallback.DEFAULT_LIFETIME) // 有効期間（デフォルト: 12時間）
        .build()
)
```

### 10.2 CommandTemplateAction

入力値をコマンドに埋め込んで実行する。`$(variable_name)` 形式で `DialogInput` のキーを参照する。

```java
// 例: DialogInput で "level" と "name" というキーの入力がある場合
DialogAction.commandTemplate("give $(name) diamond $(level)")
```

### 10.3 StaticAction

Adventure の `ClickEvent` を実行する。

```java
DialogAction.staticAction(ClickEvent.openUrl("https://example.com"))
```

---

## 11. イベント処理

### 11.1 PlayerCustomClickEvent

`DialogAction.customClick(Key, BinaryTagHolder)` で設定したアクションのクリックを処理する。

```java
@EventHandler
void onCustomClick(PlayerCustomClickEvent event) {
    Key key = event.getIdentifier();

    // コンフィギュレーションフェーズの場合
    if (event.getCommonConnection() instanceof PlayerConfigurationConnection configConn) {
        UUID uuid = configConn.getProfile().getId();
        // ...
    }

    // ゲームプレイ中の場合
    if (event.getCommonConnection() instanceof PlayerGameConnection gameConn) {
        Player player = gameConn.getPlayer();
        // ...
    }

    // 入力値の取得
    DialogResponseView view = event.getDialogResponseView();
    if (view != null) {
        // view.getFloat("key"), view.getString("key") 等で値を取得
    }
}
```

### 11.2 注意: getPlayer() は直接使えない

`PlayerCustomClickEvent` では `event.getPlayer()` を直接使えない。代わりに `event.getCommonConnection()` を適切な型にキャストする必要がある:

- ゲームプレイ中: `PlayerGameConnection` にキャスト → `getPlayer()` で Player を取得
- コンフィギュレーション中: `PlayerConfigurationConnection` にキャスト → `getProfile()` で GameProfile を取得

---

## 12. 組み込みダイアログ

Paper には3つの組み込みダイアログが存在する:

| 定数 | キー | 説明 |
|---|---|---|
| `Dialog.SERVER_LINKS` | `DialogKeys.SERVER_LINKS` | サーバーリンク一覧 |
| `Dialog.QUICK_ACTIONS` | `DialogKeys.QUICK_ACTIONS` | クイックアクション |
| `Dialog.CUSTOM_OPTIONS` | `DialogKeys.CUSTOM_OPTIONS` | カスタムオプション |

サーバーリンクの追加:

```java
ServerLinks links = Bukkit.getServer().getServerLinks();
// links に対してリンクを追加する
```

---

## 13. 実装パターン集

### 13.1 パターン: ブロッキング確認ダイアログ（サーバー参加時）

コンフィギュレーションフェーズでプレイヤーの同意を待つパターン。

```java
@NullMarked
public class JoinConfirmListener implements Listener {
    private final Map<UUID, CompletableFuture<Boolean>> awaitingResponse = new ConcurrentHashMap<>();

    @EventHandler
    void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        Dialog dialog = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.DIALOG)
            .get(Key.key("myplugin:join_confirm"));
        if (dialog == null) return;

        PlayerConfigurationConnection connection = event.getConnection();
        UUID uuid = connection.getProfile().getId();
        if (uuid == null) return;

        CompletableFuture<Boolean> response = new CompletableFuture<>();
        response.completeOnTimeout(false, 1, TimeUnit.MINUTES);
        awaitingResponse.put(uuid, response);

        Audience audience = connection.getAudience();
        audience.showDialog(dialog);

        // response.join() でブロッキング待機
        if (!response.join()) {
            audience.closeDialog();
            connection.disconnect(Component.text("同意が得られませんでした。"));
        }
        awaitingResponse.remove(uuid);
    }

    @EventHandler
    void onCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection configConn)) return;
        UUID uuid = configConn.getProfile().getId();
        if (uuid == null) return;

        Key key = event.getIdentifier();
        if (key.equals(Key.key("myplugin:agree"))) {
            complete(uuid, true);
        } else if (key.equals(Key.key("myplugin:disagree"))) {
            complete(uuid, false);
        }
    }

    @EventHandler
    void onDisconnect(PlayerConnectionCloseEvent event) {
        awaitingResponse.remove(event.getPlayerUniqueId());
    }

    private void complete(UUID uuid, boolean value) {
        CompletableFuture<Boolean> future = awaitingResponse.get(uuid);
        if (future != null) future.complete(value);
    }
}
```

### 13.2 パターン: 入力フォーム（コールバック方式）

ゲームプレイ中にプレイヤーから入力を受け取るパターン。

```java
Dialog inputDialog = Dialog.create(builder -> builder.empty()
    .base(DialogBase.builder(Component.text("経験値設定"))
        .inputs(List.of(
            DialogInput.numberRange("level", Component.text("レベル", NamedTextColor.GREEN), 0f, 100f)
                .step(1f).initial(0f).width(300).build(),
            DialogInput.numberRange("exp", Component.text("経験値%", NamedTextColor.GREEN), 0f, 100f)
                .step(1f).initial(0f).width(300)
                .labelFormat("%s: %s%%")
                .build()
        ))
        .build()
    )
    .type(DialogType.confirmation(
        ActionButton.create(
            Component.text("確定", TextColor.color(0xAEFFC1)),
            Component.text("入力を確定します"),
            100,
            DialogAction.customClick(
                (view, audience) -> {
                    int level = view.getFloat("level").intValue();
                    float exp = view.getFloat("exp").floatValue();
                    if (audience instanceof Player player) {
                        player.setLevel(level);
                        player.setExp(exp / 100f);
                        player.sendMessage(Component.text("設定しました！"));
                    }
                },
                ClickCallback.Options.builder().uses(1).build()
            )
        ),
        ActionButton.create(
            Component.text("キャンセル", TextColor.color(0xFFA0B1)),
            null, 100, null  // null action = ダイアログを閉じるだけ
        )
    ))
);

player.showDialog(inputDialog);
```

### 13.3 パターン: コマンドテンプレートによる入力処理

サーバー側イベントリスナー不要で、入力値をコマンドに直接埋め込む方式。

```java
Dialog commandDialog = Dialog.create(builder -> builder.empty()
    .base(DialogBase.builder(Component.text("テレポート先の座標"))
        .inputs(List.of(
            DialogInput.numberRange("x", Component.text("X座標"), -1000f, 1000f)
                .step(1f).initial(0f).width(300).build(),
            DialogInput.numberRange("z", Component.text("Z座標"), -1000f, 1000f)
                .step(1f).initial(0f).width(300).build()
        ))
        .build()
    )
    .type(DialogType.confirmation(
        ActionButton.create(
            Component.text("テレポート"), null, 100,
            // $(x), $(z) は DialogInput のキーに対応する
            DialogAction.commandTemplate("tp @s $(x) 64 $(z)")
        ),
        ActionButton.create(Component.text("キャンセル"), null, 100, null)
    ))
);
```

---

## 14. 必要なインポート文

```java
// Dialog 本体
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;

// Dialog 構成要素
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;

// Dialog タイプ
import io.papermc.paper.registry.data.dialog.type.DialogType;

// Dialog アクション
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;

// レジストリ関連
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;

// イベント
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.event.player.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerConnectionCloseEvent;

// コネクション
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;

// Adventure (Component, Key 等)
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.key.Key;
```

---

## 15. よくある注意点

1. **Dialog は base と type の両方が必須**: どちらか欠けるとエラーになる。
2. **Escキーで閉じさせたくない場合**: `canCloseWithEscape(false)` を明示的に設定する。
3. **コンフィギュレーションフェーズでの使用**: `AsyncPlayerConnectionConfigureEvent` 内で `CompletableFuture` を使ってプレイヤーの応答を待つ。`response.join()` でブロッキング待機する。
4. **レジストリ登録はブートストラッパーで行う**: 通常のプラグイン `onEnable` ではなく、`PluginBootstrap#bootstrap` で行う。
5. **PlayerCustomClickEvent でのプレイヤー取得**: `event.getPlayer()` ではなく `event.getCommonConnection()` を `PlayerGameConnection` にキャストして `getPlayer()` を呼ぶ。
6. **action が null のボタン**: ダイアログを閉じるだけの動作になる。意図的に使用可能。
7. **コールバック方式の有効期間**: デフォルトで12時間、`ClickCallback.Options` で制御可能。
8. **`completeOnTimeout` の活用**: コンフィギュレーションフェーズでの待機にタイムアウトを設定して、無応答プレイヤーを適切に処理する。