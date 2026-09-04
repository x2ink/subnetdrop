# Home navigation and chat return verification — 2026-09-05

## Scope

- Keep only Nearby and Settings in the home bottom navigation.
- Render the Android home status-bar backing and home header in white.
- Remove elevation from the chat header and composer and make the chat page root opaque.
- Prevent the outgoing Navigation 3 entry from transiently showing only file messages after selection is cleared.
- Filter retained text-message state by conversation ID before merging it with file-transfer state.

## Automated verification

```shell
./gradlew :core:jvmTest :app:shared:jvmTest \
  :app:shared:compileAndroidMain :app:desktopApp:compileKotlin
```

Result: `BUILD SUCCESSFUL in 2s`; 47 actionable tasks, 11 executed and 36 up-to-date.

`SharedCommonTest.chatTimelineInterleavesItemsAndFiltersOtherConversationsAndPeers` supplies a text message from a
different conversation and proves it is excluded from the selected conversation timeline. Android and desktop source-set
compilation proves the two-item `HomeSection` exhaustiveness and updated Koin ViewModel constructor.

## Static checks

```shell
rg -n "HomeSection\\.CHATS|onConversationSelected|viewModel\\.conversations|openConversation|ObserveConversationsUseCase" \
  app core -g '*.kt'
rg -n "shadowElevation|tonalElevation" \
  app/shared/src/commonMain/kotlin/ink/x2/subnetdrop/ui/ChatScreen.kt
git diff --check
```

Expected result: both searches have no matches and the diff check reports no whitespace errors.

No Android APK was installed. Physical-device frame-by-frame return behavior and exact OEM status-bar pixels therefore
remain visual verification boundaries; the state transition that previously produced a file-only frame has been removed
at its source.
