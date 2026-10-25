Directory structure:
└── hanzodev30-ghostide/
    ├── readme.md
    ├── gradle.properties
    ├── gradlew
    ├── gradlew.bat
    ├── LICENSE
    ├── Lsp.md
    ├── Lspfa.md
    ├── Plugin.md
    ├── Pluginfa.md
    ├── ThemeMakerEn.md
    ├── ThemeMakerFa.md
    ├── update.json
    ├── aimodel/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── ir/
    │           │       └── hanzodev1375/
    │           │           └── ghostide/
    │           │               └── ai/
    │           │                   ├── chat/
    │           │                   │   ├── AttachedFilesAdapter.java
    │           │                   │   ├── ChatAdapter.java
    │           │                   │   ├── ChatFadeView.java
    │           │                   │   ├── ChatMarkwon.java
    │           │                   │   ├── GlassChip.java
    │           │                   │   ├── HistoryBottomSheetFragment.java
    │           │                   │   ├── OpencodeModelPickerFragment.java
    │           │                   │   └── OpenRouterModelPickerFragment.java
    │           │                   ├── database/
    │           │                   │   ├── ChatContract.java
    │           │                   │   ├── ChatDatabaseHelper.java
    │           │                   │   └── ChatRepository.java
    │           │                   ├── model/
    │           │                   │   ├── AttachedFile.java
    │           │                   │   ├── ChatMessage.java
    │           │                   │   └── OpencodeModelInfo.java
    │           │                   ├── network/
    │           │                   │   ├── AiClient.java
    │           │                   │   ├── AiClientFactory.java
    │           │                   │   ├── ClaudeClient.java
    │           │                   │   ├── GeminiClient.java
    │           │                   │   ├── OpenAiCompatibleClient.java
    │           │                   │   ├── OpencodeClient.java
    │           │                   │   └── OpenRouterModelsClient.java
    │           │                   └── utils/
    │           │                       ├── AiConstants.java
    │           │                       ├── AiPreferencesUtils.java
    │           │                       └── FileReadUtils.java
    │           └── res/
    │               ├── drawable/
    │               │   ├── bg_remove_button.xml
    │               │   ├── ic_ai_robot.xml
    │               │   ├── ic_attach_file.xml
    │               │   ├── ic_close.xml
    │               │   ├── ic_delete.xml
    │               │   ├── ic_error.xml
    │               │   ├── ic_send.xml
    │               │   └── ic_settings.xml
    │               ├── layout/
    │               │   ├── activity_ai_chat.xml
    │               │   ├── bottom_sheet_history_fragment.xml
    │               │   ├── bottom_sheet_opencode_models.xml
    │               │   ├── item_attached_file.xml
    │               │   ├── item_chat_ai.xml
    │               │   ├── item_chat_error.xml
    │               │   ├── item_chat_loading.xml
    │               │   ├── item_chat_user.xml
    │               │   └── item_history_chat.xml
    │               ├── menu/
    │               │   └── menu_ai_chat.xml
    │               ├── values/
    │               │   └── strings_ai.xml
    │               ├── values-ar/
    │               │   └── strings_ai.xml
    │               ├── values-fa/
    │               │   └── strings_ai.xml
    │               ├── values-hi/
    │               │   └── strings_ai.xml
    │               ├── values-pt/
    │               │   └── strings_ai.xml
    │               ├── values-ru/
    │               │   └── strings_ai.xml
    │               └── values-zh/
    │                   └── strings_ai.xml
    ├── app/
    │   ├── my-release-key.jks
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── assets/
    │           │   ├── css/
    │           │   │   └── color.kj
    │           │   ├── data/
    │           │   │   └── symbol.json
    │           │   ├── shell/
    │           │   │   ├── init.sh
    │           │   │   └── weblsp.sh
    │           │   └── templates/
    │           │       ├── android/
    │           │       │   └── wrapper/
    │           │       │       ├── gradle-wrapper.jar
    │           │       │       ├── gradle-wrapper.properties
    │           │       │       ├── gradlew
    │           │       │       └── gradlew.bat
    │           │       ├── c/
    │           │       │   ├── gitignore
    │           │       │   ├── main.c
    │           │       │   └── Makefile
    │           │       ├── cpp/
    │           │       │   ├── CMakeLists.txt
    │           │       │   ├── gitignore
    │           │       │   └── main.cpp
    │           │       ├── flutter/
    │           │       │   ├── README.md
    │           │       │   ├── analysis_options.yaml
    │           │       │   ├── gitignore
    │           │       │   ├── pubspec.yaml
    │           │       │   ├── android/
    │           │       │   │   ├── gradle.properties
    │           │       │   │   ├── app/
    │           │       │   │   │   └── src/
    │           │       │   │   │       └── main/
    │           │       │   │   │           ├── AndroidManifest.xml
    │           │       │   │   │           ├── kotlin/
    │           │       │   │   │           │   └── MainActivity.kt
    │           │       │   │   │           └── res/
    │           │       │   │   │               ├── drawable/
    │           │       │   │   │               │   └── launch_background.xml
    │           │       │   │   │               └── values/
    │           │       │   │   │                   └── styles.xml
    │           │       │   │   └── gradle/
    │           │       │   │       └── wrapper/
    │           │       │   │           └── gradle-wrapper.properties
    │           │       │   ├── ios/
    │           │       │   │   ├── Podfile
    │           │       │   │   └── Runner/
    │           │       │   │       ├── AppDelegate.swift
    │           │       │   │       ├── Info.plist
    │           │       │   │       └── Assets.xcassets/
    │           │       │   │           └── AppIcon.appiconset/
    │           │       │   │               └── Contents.json
    │           │       │   ├── lib/
    │           │       │   │   └── main.dart
    │           │       │   ├── linux/
    │           │       │   │   └── CMakeLists.txt
    │           │       │   ├── test/
    │           │       │   │   └── widget_test.dart
    │           │       │   └── web/
    │           │       │       ├── index.html
    │           │       │       └── manifest.json
    │           │       ├── html/
    │           │       │   ├── index.html
    │           │       │   ├── script.js
    │           │       │   └── style.css
    │           │       ├── java/
    │           │       │   ├── gitignore
    │           │       │   └── Main.java
    │           │       ├── nodejs/
    │           │       │   ├── gitignore
    │           │       │   ├── index.js
    │           │       │   └── package.json
    │           │       ├── php/
    │           │       │   ├── composer.json
    │           │       │   ├── gitignore
    │           │       │   └── index.php
    │           │       ├── python/
    │           │       │   ├── gitignore
    │           │       │   ├── main.py
    │           │       │   └── requirements.txt
    │           │       ├── python_c/
    │           │       │   ├── gitignore
    │           │       │   ├── lib.c
    │           │       │   ├── lib.h
    │           │       │   ├── main.py
    │           │       │   └── Makefile
    │           │       └── ruby/
    │           │           ├── Gemfile
    │           │           ├── gitignore
    │           │           └── main.rb
    │           ├── java/
    │           │   └── ir/
    │           │       ├── hanzodev1375/
    │           │       │   └── ghostide/
    │           │       │       ├── GhostIdeAppLoader.java
    │           │       │       ├── SplashActivity.java
    │           │       │       ├── activity/
    │           │       │       │   ├── AboutActivity.java
    │           │       │       │   ├── AiChatActivity.java
    │           │       │       │   ├── BaseCompat.java
    │           │       │       │   ├── EditorActivity.java
    │           │       │       │   ├── ErrorManagerActivity.java
    │           │       │       │   ├── ImageViewerActivity.java
    │           │       │       │   ├── PluginManagerActivity.java
    │           │       │       │   ├── PluginScreenActivity.java
    │           │       │       │   ├── SettingActivity.java
    │           │       │       │   ├── StoreActivity.java
    │           │       │       │   └── WebViewActivity.java
    │           │       │       ├── adapters/
    │           │       │       │   ├── BreadcrumbAdapter.java
    │           │       │       │   ├── CodeRunnerHostAdapter.java
    │           │       │       │   ├── CommandAdapter.java
    │           │       │       │   ├── ContributorAdapter.java
    │           │       │       │   ├── DiagnosticsAdapter.java
    │           │       │       │   ├── EditorHostAdapter.java
    │           │       │       │   ├── EditorPagerAdapter.java
    │           │       │       │   ├── FileManagerAdapter.java
    │           │       │       │   ├── FileManagerHostAdapter.java
    │           │       │       │   ├── ImagePagerAdapter.java
    │           │       │       │   ├── InstalledPluginAdapter.java
    │           │       │       │   ├── NavAdapter.java
    │           │       │       │   ├── OpenFilesAdapter.java
    │           │       │       │   ├── OutlineAdapter.java
    │           │       │       │   ├── PluginPopupAdapter.java
    │           │       │       │   ├── SettingsAdapter.java
    │           │       │       │   ├── SnippetAdapter.java
    │           │       │       │   ├── SyncHostAdapter.java
    │           │       │       │   ├── SysmbolbarAdapter.java
    │           │       │       │   ├── ThemeFilesAdapter.java
    │           │       │       │   ├── ToolbarAdapter.java
    │           │       │       │   ├── ToolbarListAdapter.java
    │           │       │       │   ├── UiFeedbackHostImpl.java
    │           │       │       │   └── ZipBrowserAdapter.java
    │           │       │       ├── appicon/
    │           │       │       │   ├── AppIcon.java
    │           │       │       │   ├── AppIconAdapter.java
    │           │       │       │   ├── AppIconChooserDialogBuilder.java
    │           │       │       │   └── AppIconManager.java
    │           │       │       ├── bookmark/
    │           │       │       │   ├── BookmarkAdapter.java
    │           │       │       │   ├── BookmarkBottomSheet.java
    │           │       │       │   ├── BookmarkDatabaseHelper.java
    │           │       │       │   ├── BookmarkEntity.java
    │           │       │       │   ├── BookmarkRepository.java
    │           │       │       │   └── BookmarkViewModel.java
    │           │       │       ├── commands/
    │           │       │       │   └── CommandPaletteRegistry.java
    │           │       │       ├── customui/
    │           │       │       │   ├── EditorStatusBar.java
    │           │       │       │   ├── EmptyState.java
    │           │       │       │   ├── ExpandableLayout.java
    │           │       │       │   ├── FloatingToolbarView.java
    │           │       │       │   ├── GhostIdeEditorSearch.java
    │           │       │       │   ├── LayoutSymbolbar.java
    │           │       │       │   ├── MaterialGradientCard.java
    │           │       │       │   ├── NavModelPanel.java
    │           │       │       │   ├── PreferenceSwitchGroup.java
    │           │       │       │   ├── ScrollPreserveRecyclerView.java
    │           │       │       │   ├── TabCustomView.java
    │           │       │       │   └── WavyUnderlineSpan.java
    │           │       │       ├── dialogs/
    │           │       │       │   ├── CommandPaletteSheet.java
    │           │       │       │   ├── CopyProgressDialog.java
    │           │       │       │   ├── DeleteProgressDialog.java
    │           │       │       │   ├── DiagnosticsBottomSheet.java
    │           │       │       │   ├── FileSortManager.kt
    │           │       │       │   ├── OpenFilesSheet.java
    │           │       │       │   ├── OutlineSheet.java
    │           │       │       │   ├── ShortcutSettingsDialog.java
    │           │       │       │   └── SnippetManagerSheet.java
    │           │       │       ├── editorlangs/
    │           │       │       │   └── LanguageManager.java
    │           │       │       ├── enums/
    │           │       │       │   ├── FileState.java
    │           │       │       │   ├── ProjectType.java
    │           │       │       │   └── SortType.java
    │           │       │       ├── fileevents/
    │           │       │       │   ├── BaseFileEventCheck.java
    │           │       │       │   ├── FileCreatedEvent.java
    │           │       │       │   ├── FileEventCheck.java
    │           │       │       │   └── FileRenamedEvent.java
    │           │       │       ├── fragments/
    │           │       │       │   ├── BatchRenameSheet.java
    │           │       │       │   ├── EditorFragment.java
    │           │       │       │   ├── FilePropertiesSheet.java
    │           │       │       │   ├── ImageViewerFragment.java
    │           │       │       │   └── MarkDownPreview.java
    │           │       │       ├── glide/
    │           │       │       │   ├── AppGlide.java
    │           │       │       │   ├── apkicon/
    │           │       │       │   │   ├── ApkIconFetcher.java
    │           │       │       │   │   └── ApkIconModelLoader.java
    │           │       │       │   ├── gpl/
    │           │       │       │   │   ├── GplIcon.java
    │           │       │       │   │   ├── GplIconFetcher.java
    │           │       │       │   │   └── GplIconLoader.java
    │           │       │       │   ├── music/
    │           │       │       │   │   ├── Mp3CoverLoader.java
    │           │       │       │   │   ├── Mp3CoverLoaderFactory.java
    │           │       │       │   │   └── Mp3CoverModelLoader.java
    │           │       │       │   ├── svg/
    │           │       │       │   │   ├── SvgDecoder.java
    │           │       │       │   │   └── SvgDrawableTranscoder.java
    │           │       │       │   └── xml/
    │           │       │       │       ├── VectorModel.java
    │           │       │       │       ├── VectorModelFetcher.java
    │           │       │       │       ├── VectorModelLoader.java
    │           │       │       │       └── VectorModelLoaderFactory.java
    │           │       │       ├── helper/
    │           │       │       │   ├── FileGitHelper.java
    │           │       │       │   ├── PluginPopupHelper.java
    │           │       │       │   └── ZipModeHelper.java
    │           │       │       ├── history/
    │           │       │       │   ├── HistoryAdapter.java
    │           │       │       │   ├── HistoryBottomSheet.java
    │           │       │       │   ├── HistoryEntity.java
    │           │       │       │   ├── HistoryRepository.java
    │           │       │       │   └── HistoryViewModel.java
    │           │       │       ├── iconpack/
    │           │       │       │   └── IconPackManager.java
    │           │       │       ├── interfaces/
    │           │       │       │   ├── IShellUserService.java
    │           │       │       │   └── OnItemClickListener.java
    │           │       │       ├── listeners/
    │           │       │       │   └── LspDiagnosticsEventListener.java
    │           │       │       ├── models/
    │           │       │       │   ├── CommandItem.java
    │           │       │       │   ├── Contributor.java
    │           │       │       │   ├── CursorPack.java
    │           │       │       │   ├── FileManagerModel.java
    │           │       │       │   ├── InstalledPluginInfo.java
    │           │       │       │   ├── LspState.java
    │           │       │       │   ├── NavModel.java
    │           │       │       │   ├── SettingItem.java
    │           │       │       │   ├── TabModel.java
    │           │       │       │   ├── ToolbarModel.java
    │           │       │       │   ├── ZipEntryModel.java
    │           │       │       │   └── ZipInfo.java
    │           │       │       ├── mvvm/
    │           │       │       │   └── viewmodel/
    │           │       │       │       ├── AboutViewModel.java
    │           │       │       │       ├── EditorViewModel.java
    │           │       │       │       ├── FileViewModel.java
    │           │       │       │       ├── GitHubStarViewModel.java
    │           │       │       │       └── LspViewModel.java
    │           │       │       ├── onboarding/
    │           │       │       │   ├── FeaturesFragment.java
    │           │       │       │   ├── OnboardingActivity.java
    │           │       │       │   ├── OnboardingAnim.java
    │           │       │       │   ├── OnboardingPrefs.java
    │           │       │       │   ├── OnboardingViewModel.java
    │           │       │       │   ├── TerminalFragment.java
    │           │       │       │   └── WelcomeFragment.java
    │           │       │       ├── paged/
    │           │       │       │   └── PagedEditSession.java
    │           │       │       ├── plugin/
    │           │       │       │   ├── BackgroundPluginManager.java
    │           │       │       │   ├── BackgroundRendererBridge.java
    │           │       │       │   ├── FloatingPanelWindow.java
    │           │       │       │   ├── Plugin.java
    │           │       │       │   ├── PluginConfigModel.java
    │           │       │       │   ├── PluginContext.java
    │           │       │       │   ├── PluginHostRouter.java
    │           │       │       │   ├── PluginLoader.java
    │           │       │       │   ├── PluginManager.java
    │           │       │       │   ├── PluginPanelHost.java
    │           │       │       │   ├── PluginPopupController.java
    │           │       │       │   ├── PluginPopupDispatcher.java
    │           │       │       │   ├── PluginSettingsActivity.java
    │           │       │       │   ├── gpl/
    │           │       │       │   │   ├── AndroidPluginLogger.java
    │           │       │       │   │   ├── DefaultPluginContext.java
    │           │       │       │   │   ├── GplInstalledPlugins.java
    │           │       │       │   │   ├── GplManifest.java
    │           │       │       │   │   ├── GplManifestReader.java
    │           │       │       │   │   ├── GplPluginContextWrapper.java
    │           │       │       │   │   ├── GplPluginLoader.java
    │           │       │       │   │   ├── LoadedGplPlugin.java
    │           │       │       │   │   ├── PluginScopedExtensions.java
    │           │       │       │   │   └── PluginStorageImpl.java
    │           │       │       │   └── install/
    │           │       │       │       └── GplPluginInstallerHost.java
    │           │       │       ├── postman/
    │           │       │       │   ├── CollectionDetailActivity.java
    │           │       │       │   ├── CollectionsActivity.java
    │           │       │       │   ├── HistoryActivity.java
    │           │       │       │   ├── PostManActivity.java
    │           │       │       │   ├── SaveRequestBottomSheet.java
    │           │       │       │   ├── SettingsActivity.java2
    │           │       │       │   ├── adapter/
    │           │       │       │   │   ├── CollectionAdapter.java
    │           │       │       │   │   ├── HistoryAdapter.java
    │           │       │       │   │   ├── KeyValueAdapter.java
    │           │       │       │   │   └── SavedRequestAdapter.java
    │           │       │       │   ├── data/
    │           │       │       │   │   ├── AppRepository.java
    │           │       │       │   │   └── DbHelper.java
    │           │       │       │   ├── model/
    │           │       │       │   │   ├── HistoryItem.java
    │           │       │       │   │   ├── KeyValueItem.java
    │           │       │       │   │   ├── RequestCollection.java
    │           │       │       │   │   ├── RequestSnapshot.java
    │           │       │       │   │   └── SavedRequest.java
    │           │       │       │   ├── network/
    │           │       │       │   │   └── HttpEngine.java
    │           │       │       │   └── util/
    │           │       │       │       ├── ColorUtils.java
    │           │       │       │       ├── JsonUtils.java
    │           │       │       │       ├── PrefsManager.java
    │           │       │       │       ├── SimpleTextWatcher.java
    │           │       │       │       ├── TimeUtils.java
    │           │       │       │       └── UiUtils.java
    │           │       │       ├── project/
    │           │       │       │   ├── android/
    │           │       │       │   │   ├── AndroidCategory.java
    │           │       │       │   │   ├── AndroidProjectConfig.java
    │           │       │       │   │   ├── AndroidProjectGenerator.java
    │           │       │       │   │   ├── AndroidStudioProjectSheet.java
    │           │       │       │   │   └── AndroidTemplate.java
    │           │       │       │   ├── DslType.java
    │           │       │       │   ├── LibraryManager.java
    │           │       │       │   ├── ModuleCreator.java
    │           │       │       │   ├── ModuleScanner.java
    │           │       │       │   ├── NewModuleDialog.java
    │           │       │       │   ├── NewProjectDialog.java
    │           │       │       │   └── ProjectCreator.java
    │           │       │       ├── pulse/
    │           │       │       │   ├── DirectoryPulse.java
    │           │       │       │   ├── PulseBridge.java
    │           │       │       │   ├── PulseListener.java
    │           │       │       │   └── PulseService.java
    │           │       │       ├── refactor/
    │           │       │       │   ├── rename/
    │           │       │       │   │   ├── AndroidModuleValidator.java
    │           │       │       │   │   ├── BackgroundExecutor.java
    │           │       │       │   │   ├── CancellableTask.java
    │           │       │       │   │   ├── CancellationToken.java
    │           │       │       │   │   ├── CompositePackageRefactorEngine.java
    │           │       │       │   │   ├── DirectoryPackageMoveEngine.java
    │           │       │       │   │   ├── FileRenameNotifier.java
    │           │       │       │   │   ├── GradleRefactor.java
    │           │       │       │   │   ├── ManifestRefactor.java
    │           │       │       │   │   ├── PackageMoveEngine.java
    │           │       │       │   │   ├── PackagePreview.java
    │           │       │       │   │   ├── PackageRefactorEngine.java
    │           │       │       │   │   ├── PackageRenameManager.java
    │           │       │       │   │   ├── PackageScanner.java
    │           │       │       │   │   ├── PackageValidator.java
    │           │       │       │   │   ├── ProgressReporter.java
    │           │       │       │   │   ├── RenameCancelledException.java
    │           │       │       │   │   ├── RollbackManager.java
    │           │       │       │   │   ├── lang/
    │           │       │       │   │   │   ├── ClassReferenceRewriter.java
    │           │       │       │   │   │   ├── ImportInfo.java
    │           │       │       │   │   │   ├── ImportScanner.java
    │           │       │       │   │   │   ├── JavaLexer.java
    │           │       │       │   │   │   ├── JavaRefactor.java
    │           │       │       │   │   │   ├── KotlinLexer.java
    │           │       │       │   │   │   ├── KotlinRefactor.java
    │           │       │       │   │   │   ├── PackageTextRewriter.java
    │           │       │       │   │   │   ├── SimpleIdentifierRewriter.java
    │           │       │       │   │   │   └── SourceRegion.java
    │           │       │       │   │   ├── model/
    │           │       │       │   │   │   ├── DirectoryOperation.java
    │           │       │       │   │   │   ├── ModuleType.java
    │           │       │       │   │   │   ├── PreviewEntry.java
    │           │       │       │   │   │   ├── PreviewResult.java
    │           │       │       │   │   │   ├── RenameCallback.java
    │           │       │       │   │   │   ├── RenamePlan.java
    │           │       │       │   │   │   ├── RenameProgress.java
    │           │       │       │   │   │   ├── RollbackEntry.java
    │           │       │       │   │   │   ├── ScanResult.java
    │           │       │       │   │   │   ├── SourceEdit.java
    │           │       │       │   │   │   ├── SourceEditType.java
    │           │       │       │   │   │   └── ValidationResult.java
    │           │       │       │   │   └── ui/
    │           │       │       │   │       ├── PreviewEntryAdapter.java
    │           │       │       │   │       ├── RenamePackageBottomSheet.java
    │           │       │       │   │       └── RenamePackageViewModel.java
    │           │       │       │   └── renameclass/
    │           │       │       │       ├── ClassFileTarget.java
    │           │       │       │       ├── ClassNameValidator.java
    │           │       │       │       ├── ClassRenameCallback.java
    │           │       │       │       ├── ClassRenameEngine.java
    │           │       │       │       ├── ClassRenameManager.java
    │           │       │       │       ├── ClassScanner.java
    │           │       │       │       ├── ClassScanResult.java
    │           │       │       │       └── ui/
    │           │       │       │           ├── RenameClassBottomSheet.java
    │           │       │       │           └── RenameClassViewModel.java
    │           │       │       ├── runer/
    │           │       │       │   └── CodeRuner.java
    │           │       │       ├── settings/
    │           │       │       │   ├── AiSettingSection.java
    │           │       │       │   ├── AppSettingSection.java
    │           │       │       │   ├── BackgroundPluginSettingSection.java
    │           │       │       │   ├── CodeRunnerSetting.java
    │           │       │       │   ├── EditorSettingSection.java
    │           │       │       │   ├── IconPackSettingSection.java
    │           │       │       │   ├── LspSettingSection.java
    │           │       │       │   ├── PluginSettingSection.java
    │           │       │       │   └── SettingSection.java
    │           │       │       ├── shizuku/
    │           │       │       │   ├── ShellUserService.java
    │           │       │       │   └── ShizukuManager.java
    │           │       │       ├── snippets/
    │           │       │       │   ├── SnippetEntry.java
    │           │       │       │   ├── UserSnippetCompletionProvider.java
    │           │       │       │   └── UserSnippetStore.java
    │           │       │       ├── splitlayout/
    │           │       │       │   ├── EditorPaneFragment.java
    │           │       │       │   ├── SplitEditorPagerAdapter.java
    │           │       │       │   ├── SplitLayoutPopup.java
    │           │       │       │   ├── SplitPaneContainerLayout.java
    │           │       │       │   └── SplitPreviewView.java
    │           │       │       ├── store/
    │           │       │       │   ├── ThemeStoreFragment.java
    │           │       │       │   ├── ThemeStorePreviewSheet.java
    │           │       │       │   └── ThemeStorePreviewViewModel.java
    │           │       │       ├── tasks/
    │           │       │       │   └── FileChangeReceiver.java
    │           │       │       ├── terminal/
    │           │       │       │   ├── DebianBootstrap.java
    │           │       │       │   ├── DebianInstaller.java
    │           │       │       │   ├── GhostTerminalSessionClient.java
    │           │       │       │   ├── GhostTerminalViewClient.java
    │           │       │       │   ├── ProotExec.java
    │           │       │       │   ├── ProotSessionFactory.java
    │           │       │       │   ├── RootfsExtractor.java
    │           │       │       │   ├── TerminalBackupManager.java
    │           │       │       │   ├── TerminalColorsUtil.java
    │           │       │       │   ├── TerminalInputDock.java
    │           │       │       │   ├── TerminalPrompt.java
    │           │       │       │   ├── TerminalSessionFactory.java
    │           │       │       │   ├── TerminalSessionFragment.java
    │           │       │       │   ├── TerminalSessionService.java
    │           │       │       │   ├── TerminalTab.java
    │           │       │       │   ├── TerminalViewModel.java
    │           │       │       │   ├── activity/
    │           │       │       │   │   └── TerminalActivity.java
    │           │       │       │   ├── adapters/
    │           │       │       │   │   └── TerminalTabAdapter.java
    │           │       │       │   └── sheet/
    │           │       │       │       └── TerminalBottomSheetFragment.java
    │           │       │       ├── themeengine/
    │           │       │       │   ├── CircleImageView.java
    │           │       │       │   ├── ColorAdapter.java
    │           │       │       │   ├── GthCanvasDrawable.java
    │           │       │       │   ├── Theme.java
    │           │       │       │   ├── ThemeChooserDialogBuilder.java
    │           │       │       │   ├── ThemeEngine.java
    │           │       │       │   ├── ThemeEngineExtensions.java
    │           │       │       │   └── ThemeMode.java
    │           │       │       ├── translator/
    │           │       │       │   ├── model/
    │           │       │       │   │   ├── AndroidLanguage.java
    │           │       │       │   │   ├── StringEntry.java
    │           │       │       │   │   └── TranslationProgress.java
    │           │       │       │   ├── ui/
    │           │       │       │   │   └── StringsTranslatorSheet.java
    │           │       │       │   ├── util/
    │           │       │       │   │   ├── StringsTranslator.java
    │           │       │       │   │   └── StringsXmlParser.java
    │           │       │       │   └── viewmodel/
    │           │       │       │       └── TranslatorViewModel.java
    │           │       │       └── utils/
    │           │       │           ├── ArchiveExtractUtil.java
    │           │       │           ├── BitmapTransformation.java
    │           │       │           ├── BlurHelper.java
    │           │       │           ├── BlurTransformation.java
    │           │       │           ├── ConstKeys.java
    │           │       │           ├── EditorFileStats.java
    │           │       │           ├── EditorGitStatus.java
    │           │       │           ├── EditorGlassMenu.java
    │           │       │           ├── FastBlur.java
    │           │       │           ├── FileExtensionUtils.java
    │           │       │           ├── FileUtil.java
    │           │       │           ├── GroupedRowDecoration.java
    │           │       │           ├── Icon.java
    │           │       │           ├── InfoRows.java
    │           │       │           ├── LocaleDirection.java
    │           │       │           ├── LocaleHelper.java
    │           │       │           ├── NetworkChangeReceiver.java
    │           │       │           ├── ObjectUtil.java
    │           │       │           ├── PathManager.java
    │           │       │           ├── PermissionUtils.java
    │           │       │           ├── RSBlur.java
    │           │       │           ├── ShapeUtil.java
    │           │       │           ├── ShortcutHelper.java
    │           │       │           ├── StackTraceInfo.java
    │           │       │           ├── StorageUtils.java
    │           │       │           ├── UriFileImporter.java
    │           │       │           ├── WebDownloadHelper.java
    │           │       │           ├── WebViewSetting.java
    │           │       │           ├── ZipUtil.java
    │           │       │           └── zip/
    │           │       │               └── ZipOperationManager.java
    │           │       └── theme/
    │           │           ├── ThemeUtils.java
    │           │           └── themeeditor/
    │           │               ├── RootHolder.java
    │           │               ├── ThemePreviewBottomSheet.java
    │           │               └── ThemeReferenceBottomSheet.java
    │           └── res/
    │               ├── anim/
    │               │   ├── list_item_enter.xml
    │               │   ├── popup_split_enter.xml
    │               │   └── popup_split_exit.xml
    │               ├── drawable/
    │               │   ├── add.xml
    │               │   ├── bg_chip_rounded.xml
    │               │   ├── bg_code_input.xml
    │               │   ├── bg_drag_handle.xml
    │               │   ├── bg_git_marker.xml
    │               │   ├── bg_icon_check_badge.xml
    │               │   ├── bg_input_dock.xml
    │               │   ├── bg_input_row.xml
    │               │   ├── bg_position_indicator.xml
    │               │   ├── bg_tab_indicator.xml
    │               │   ├── bg_terminal_tab_dot.xml
    │               │   ├── bg_terminal_tab_indicator.xml
    │               │   ├── bg_url_bar.xml
    │               │   ├── check_24px.xml
    │               │   ├── folder.xml
    │               │   ├── ic_add.xml
    │               │   ├── ic_android.xml
    │               │   ├── ic_arrow_down.xml
    │               │   ├── ic_arrow_forward.xml
    │               │   ├── ic_back.xml
    │               │   ├── ic_bug_report.xml
    │               │   ├── ic_close.xml
    │               │   ├── ic_content_copy.xml
    │               │   ├── ic_copy.xml
    │               │   ├── ic_cut.xml
    │               │   ├── ic_delete.xml
    │               │   ├── ic_directions_car.xml
    │               │   ├── ic_download.xml
    │               │   ├── ic_drag_handle.xml
    │               │   ├── ic_edit.xml
    │               │   ├── ic_expand.xml
    │               │   ├── ic_fileicon.xml
    │               │   ├── ic_folder.xml
    │               │   ├── ic_folder_open.xml
    │               │   ├── ic_ghost_empty.xml
    │               │   ├── ic_git.xml
    │               │   ├── ic_git_add.xml
    │               │   ├── ic_history.xml
    │               │   ├── ic_info.xml
    │               │   ├── ic_layout_grid.xml
    │               │   ├── ic_onboarding_check.xml
    │               │   ├── ic_onboarding_features.xml
    │               │   ├── ic_onboarding_perm_files.xml
    │               │   ├── ic_onboarding_perm_install.xml
    │               │   ├── ic_onboarding_perm_notifications.xml
    │               │   ├── ic_onboarding_terminal.xml
    │               │   ├── ic_open_files.xml
    │               │   ├── ic_panel.xml
    │               │   ├── ic_paste.xml
    │               │   ├── ic_phone_android.xml
    │               │   ├── ic_tv.xml
    │               │   ├── ic_watch.xml
    │               │   ├── ic_photo.xml
    │               │   ├── ic_plugin_editor.xml
    │               │   ├── ic_plugin_lsp.xml
    │               │   ├── ic_plugin_misc.xml
    │               │   ├── ic_refresh.xml
    │               │   ├── ic_resize.xml
    │               │   ├── ic_restore.xml
    │               │   ├── ic_rotate.xml
    │               │   ├── ic_round_bread_crumb_arrow.xml
    │               │   ├── ic_round_check.xml
    │               │   ├── ic_save.xml
    │               │   ├── ic_send.xml
    │               │   ├── ic_settings.xml
    │               │   ├── ic_share.xml
    │               │   ├── ic_single_pane.xml
    │               │   ├── ic_split_column.xml
    │               │   ├── ic_split_row.xml
    │               │   ├── ic_star.xml
    │               │   ├── ic_zoom.xml
    │               │   ├── icon_more_vert_round.xml
    │               │   ├── menucustom.xml
    │               │   ├── more_vert.xml
    │               │   ├── outline_badge.xml
    │               │   ├── outline_check_box.xml
    │               │   ├── outline_color_lens.xml
    │               │   ├── outline_comment.xml
    │               │   ├── outline_content_copy.xml
    │               │   ├── outline_content_cut.xml
    │               │   ├── outline_content_paste.xml
    │               │   ├── outline_copy_all.xml
    │               │   ├── outline_highlight.xml
    │               │   ├── outline_keyboard.xml
    │               │   ├── outline_person.xml
    │               │   ├── outline_redo.xml
    │               │   ├── outline_save.xml
    │               │   ├── outline_search.xml
    │               │   ├── outline_select_all.xml
    │               │   ├── outline_shield.xml
    │               │   ├── outline_storage.xml
    │               │   ├── outline_undo.xml
    │               │   ├── play_arrow.xml
    │               │   ├── round_account_tree.xml
    │               │   ├── round_push_pin.xml
    │               │   ├── round_remove_red_eye.xml
    │               │   ├── save.xml
    │               │   └── settings.xml
    │               ├── interpolator/
    │               │   ├── ios_ease_in.xml
    │               │   └── ios_ease_out.xml
    │               ├── layout/
    │               │   ├── activity_about.xml
    │               │   ├── activity_collection_detail.xml
    │               │   ├── activity_collections.xml
    │               │   ├── activity_editor.xml
    │               │   ├── activity_filemanager.xml
    │               │   ├── activity_history.xml
    │               │   ├── activity_image_viewer.xml
    │               │   ├── activity_onboarding.xml
    │               │   ├── activity_plugin_manager.xml
    │               │   ├── activity_plugin_screen.xml
    │               │   ├── activity_plugin_settings.xml
    │               │   ├── activity_postman.xml
    │               │   ├── activity_setting.xml
    │               │   ├── activity_splash.xml
    │               │   ├── activity_terminal.xml
    │               │   ├── activity_theme_editor.xml
    │               │   ├── activity_web.xml
    │               │   ├── bottom_sheet_batch_rename.xml
    │               │   ├── bottom_sheet_android_project.xml
    │               │   ├── bottom_sheet_bookmarks.xml
    │               │   ├── bottom_sheet_file_properties.xml
    │               │   ├── bottom_sheet_history.xml
    │               │   ├── bottom_sheet_preview_theme.xml
    │               │   ├── bottom_sheet_rename_class.xml
    │               │   ├── bottom_sheet_rename_package.xml
    │               │   ├── bottom_sheet_save_request.xml
    │               │   ├── bottom_sheet_theme_refs.xml
    │               │   ├── bottom_sheet_translator.xml
    │               │   ├── custom_tab.xml
    │               │   ├── diag_sheet.xml
    │               │   ├── dialog_archive_password.xml
    │               │   ├── dialog_copy_progress.xml
    │               │   ├── dialog_create_zip.xml
    │               │   ├── dialog_delete_progress.xml
    │               │   ├── dialog_download_confirm.xml
    │               │   ├── dialog_download_progress.xml
    │               │   ├── dialog_edit_shortcut.xml
    │               │   ├── dialog_element_editor.xml
    │               │   ├── dialog_file_sort.xml
    │               │   ├── dialog_new_module.xml
    │               │   ├── dialog_new_project.xml
    │               │   ├── dialog_sheet_commands.xml
    │               │   ├── dialog_sheet_open_files.xml
    │               │   ├── dialog_sheet_outline.xml
    │               │   ├── dialog_sheet_snippets.xml
    │               │   ├── dialog_slider.xml
    │               │   ├── dialog_snippet_edit.xml
    │               │   ├── dialog_theme_picker.xml
    │               │   ├── dialog_zip_progress.xml
    │               │   ├── editor_fragment.xml
    │               │   ├── editor_status_bar.xml
    │               │   ├── errormanager.xml
    │               │   ├── floating_panel_window.xml
    │               │   ├── fragment_android_studio_project.xml
    │               │   ├── fragment_editor_pane.xml
    │               │   ├── fragment_features.xml
    │               │   ├── fragment_image_viewer.xml
    │               │   ├── fragment_terminal.xml
    │               │   ├── fragment_terminal_session.xml
    │               │   ├── fragment_welcome.xml
    │               │   ├── glass_menu_item.xml
    │               │   ├── glass_plugin_item.xml
    │               │   ├── init_loading_overlay.xml
    │               │   ├── item_app_icon.xml
    │               │   ├── item_about_section.xml
    │               │   ├── item_bookmark.xml
    │               │   ├── item_breadcrumb.xml
    │               │   ├── item_collection.xml
    │               │   ├── item_color.xml
    │               │   ├── item_color_row.xml
    │               │   ├── item_command.xml
    │               │   ├── item_diag.xml
    │               │   ├── item_contributor.xml
    │               │   ├── item_diag_row_single.xml
    │               │   ├── item_file_manager.xml
    │               │   ├── item_filemanager_grid.xml
    │               │   ├── item_history.xml
    │               │   ├── item_history_postman.xml
    │               │   ├── item_image_row.xml
    │               │   ├── item_installed_plugin.xml
    │               │   ├── item_key_value.xml
    │               │   ├── item_onboarding_permission.xml
    │               │   ├── item_open_file.xml
    │               │   ├── item_outline.xml
    │               │   ├── item_preview_entry.xml
    │               │   ├── item_props_row.xml
    │               │   ├── item_saved_request.xml
    │               │   ├── item_slider_row.xml
    │               │   ├── item_snippet.xml
    │               │   ├── item_terminal_session_tab.xml
    │               │   ├── item_terminal_tab.xml
    │               │   ├── item_theme_grid.xml
    │               │   ├── item_theme_ref_row.xml
    │               │   ├── layout_emptystate.xml
    │               │   ├── layout_expandable.xml
    │               │   ├── layout_inputlayout.xml
    │               │   ├── layout_nav_item.xml
    │               │   ├── layout_searcher.xml
    │               │   ├── layout_switch_perfence.xml
    │               │   ├── popup_split_layout.xml
    │               │   ├── recyclerview.xml
    │               │   ├── selection_panel.xml
    │               │   ├── sheet_terminal.xml
    │               │   ├── sheet_theme_store_preview.xml
    │               │   ├── sysmbol_item.xml
    │               │   ├── view_floating_action_toolbar.xml
    │               │   └── widget_theme_preview_sample.xml
    │               ├── menu/
    │               │   ├── bottom_nav_preview.xml
    │               │   ├── menu_history.xml
    │               │   ├── menu_main.xml
    │               │   ├── menu_settings.xml
    │               │   ├── menu_webview.xml
    │               │   ├── tab_menu.xml
    │               │   └── theme_editor_menu.xml
    │               ├── mipmap-anydpi-v26/
    │               │   └── ic_lego.xml
    │               ├── values/
    │               │   ├── arrays.xml
    │               │   ├── attrs.xml
    │               │   ├── colorlavenderpink.xml
    │               │   ├── colors.xml
    │               │   ├── colors_red.xml
    │               │   ├── colorsgreen.xml
    │               │   ├── colorslemon.xml
    │               │   ├── defaults.xml
    │               │   ├── filemanager_styles.xml
    │               │   ├── popup_anim_styles.xml
    │               │   ├── strings.xml
    │               │   ├── themes.xml
    │               │   ├── themes_red.xml
    │               │   ├── themesgreen.xml
    │               │   ├── themeslavenderpink.xml
    │               │   └── themeslemon.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-night/
    │               │   ├── colorlavenderpink.xml
    │               │   ├── colors.xml
    │               │   ├── colors_red.xml
    │               │   ├── colorsgreen.xml
    │               │   ├── colorslemon.xml
    │               │   ├── themes.xml
    │               │   ├── themes_red.xml
    │               │   ├── themesgreen.xml
    │               │   ├── themeslavenderpink.xml
    │               │   └── themeslemon.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               ├── values-zh/
    │               │   └── strings.xml
    │               └── xml/
    │                   ├── backup_rules.xml
    │                   ├── data_extraction_rules.xml
    │                   └── file_paths.xml
    ├── components/
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   ├── ir/
    │           │   │   └── hanzodev1375/
    │           │   │       └── components/
    │           │   │           ├── RenameDialogFragment.java
    │           │   │           ├── SearchLayout.java
    │           │   │           ├── TextInputDialogFragment.java
    │           │   │           ├── WebViewBottomSheetFragment.java
    │           │   │           ├── animators/
    │           │   │           │   ├── AnimationManager.java
    │           │   │           │   ├── CubicBezierInterpolator.java
    │           │   │           │   ├── GridListMorph.java
    │           │   │           │   └── SpringInterpolator.java
    │           │   │           ├── childern/
    │           │   │           │   ├── BackgroundChildFactory.java
    │           │   │           │   ├── BackgroundChildRegistry.java
    │           │   │           │   ├── GifChild.java
    │           │   │           │   ├── IChild.java
    │           │   │           │   ├── ImageChild.java
    │           │   │           │   ├── StackBlurTransformation.java
    │           │   │           │   ├── VideoChild.java
    │           │   │           │   ├── ViewChilder.java
    │           │   │           │   ├── ViewChilderPreview.java
    │           │   │           │   └── WallpaperParallaxEffect.java
    │           │   │           ├── colors/
    │           │   │           │   ├── AccentPalette.java
    │           │   │           │   ├── ColorPickerBottomSheet.java
    │           │   │           │   ├── adapter/
    │           │   │           │   │   └── ColorAdapter.java
    │           │   │           │   └── model/
    │           │   │           │       └── ColorItem.java
    │           │   │           ├── effect/
    │           │   │           │   ├── ParticleSmasher.java
    │           │   │           │   ├── SmashAnimator.java
    │           │   │           │   ├── StarParticlesView.java
    │           │   │           │   ├── ThanosEffect.java
    │           │   │           │   ├── ThanosItemAnimator.java
    │           │   │           │   ├── Utils.java
    │           │   │           │   ├── particle/
    │           │   │           │   │   ├── DropParticle.java
    │           │   │           │   │   ├── ExplosionParticle.java
    │           │   │           │   │   ├── FloatParticle.java
    │           │   │           │   │   └── Particle.java
    │           │   │           │   └── ripple/
    │           │   │           │       ├── RippleEffect.java
    │           │   │           │       ├── SuperRipple.java
    │           │   │           │       ├── SuperRippleFallback.java
    │           │   │           │       └── WaterRipple.java
    │           │   │           ├── ftp/
    │           │   │           │   ├── adapter/
    │           │   │           │   │   └── FtpBrowserAdapter.java
    │           │   │           │   ├── impl/
    │           │   │           │   │   ├── FtpClientImpl.java
    │           │   │           │   │   ├── SftpClientImpl.java
    │           │   │           │   │   └── SmbClientImpl.java
    │           │   │           │   ├── interfaces/
    │           │   │           │   │   └── RemoteClient.java
    │           │   │           │   ├── model/
    │           │   │           │   │   └── FtpEntry.java
    │           │   │           │   └── views/
    │           │   │           │       ├── FtpBrowserSheet.java
    │           │   │           │       └── FtpConnectSheet.java
    │           │   │           ├── glass/
    │           │   │           │   ├── ButtonCompat.java
    │           │   │           │   ├── ChipCompat.java
    │           │   │           │   ├── ChipGroupCompat.java
    │           │   │           │   ├── Glass.java
    │           │   │           │   └── TabBarCompat.java
    │           │   │           ├── glsl/
    │           │   │           │   ├── GLSLShaderView.java
    │           │   │           │   ├── GlslBackgroundChild.java
    │           │   │           │   ├── GlslBackgroundFactory.java
    │           │   │           │   ├── GlslEffects.java
    │           │   │           │   └── GlslPalette.java
    │           │   │           ├── image/
    │           │   │           │   └── ZoomableImageView.kt
    │           │   │           ├── input/
    │           │   │           │   ├── TextInputEditTextGlass.java
    │           │   │           │   └── TextInputGlass.java
    │           │   │           ├── searchdata/
    │           │   │           │   ├── FileSearchEngine.java
    │           │   │           │   ├── adapter/
    │           │   │           │   │   ├── ContentMatchAdapter.java
    │           │   │           │   │   └── SearchResultAdapter.java
    │           │   │           │   ├── interfaces/
    │           │   │           │   │   └── OnLineClickListener.java
    │           │   │           │   ├── model/
    │           │   │           │   │   ├── ContentMatch.java
    │           │   │           │   │   ├── FileSearchResult.java
    │           │   │           │   │   ├── SearchMode.java
    │           │   │           │   │   ├── SearchQuery.java
    │           │   │           │   │   └── SearchType.java
    │           │   │           │   ├── ui/
    │           │   │           │   │   ├── ExcludedSearchSheet.java
    │           │   │           │   │   └── SearchBottomSheet.java
    │           │   │           │   ├── utils/
    │           │   │           │   │   └── GlobExcluder.java
    │           │   │           │   └── viewmodel/
    │           │   │           │       └── SearchViewModel.java
    │           │   │           ├── sheet/
    │           │   │           │   ├── BaseBlurBottomSheet.java
    │           │   │           │   ├── BaseSheet.java
    │           │   │           │   ├── BlurBackdrop.java
    │           │   │           │   ├── PluginSetupSheet.java
    │           │   │           │   ├── SliderSheet.java
    │           │   │           │   └── customitemsheet/
    │           │   │           │       ├── callback/
    │           │   │           │       │   └── OnSheetListener.java
    │           │   │           │       ├── model/
    │           │   │           │       │   └── SheetModel.java
    │           │   │           │       └── ui/
    │           │   │           │           ├── CustomItemSheet.java
    │           │   │           │           ├── DialogCompat.java
    │           │   │           │           ├── FabGlass.java
    │           │   │           │           ├── GlassCompat.java
    │           │   │           │           ├── LiquidGlassDialogBuilderJava.java
    │           │   │           │           └── SheetAdapter.java
    │           │   │           ├── store/
    │           │   │           │   ├── adapter/
    │           │   │           │   │   ├── FontsAdapter.java
    │           │   │           │   │   ├── IconsAdapter.java
    │           │   │           │   │   ├── PluginStoreAdapter.java
    │           │   │           │   │   ├── ThemeImageAdapter.java
    │           │   │           │   │   ├── ThemesAdapter.java
    │           │   │           │   │   ├── ViewPagerAdapter.java
    │           │   │           │   │   └── WebStoreAdapter.java
    │           │   │           │   ├── api/
    │           │   │           │   │   ├── FontsApi.java
    │           │   │           │   │   ├── IconsApi.java
    │           │   │           │   │   ├── PluginStoreApi.java
    │           │   │           │   │   ├── ThemesApi.java
    │           │   │           │   │   └── WebStoreApi.java
    │           │   │           │   ├── data/
    │           │   │           │   │   ├── FontsRepository.java
    │           │   │           │   │   ├── IconsRepository.java
    │           │   │           │   │   ├── PluginRepository.java
    │           │   │           │   │   └── ThemesRepository.java
    │           │   │           │   ├── event/
    │           │   │           │   │   ├── PluginInstalledListEvent.java
    │           │   │           │   │   ├── PluginInstallEvent.java
    │           │   │           │   │   ├── PluginProgressEvent.java
    │           │   │           │   │   ├── PluginResultEvent.java
    │           │   │           │   │   ├── PluginSetupEvent.java
    │           │   │           │   │   ├── PluginSetupRequestEvent.java
    │           │   │           │   │   ├── PluginStoreEvent.java
    │           │   │           │   │   └── ThemeInstalledEvent.java
    │           │   │           │   ├── fragments/
    │           │   │           │   │   ├── FontsFragment.java
    │           │   │           │   │   ├── IconsFragment.java
    │           │   │           │   │   ├── PluginStoreFragment.java
    │           │   │           │   │   ├── StoreSectionFragment.java
    │           │   │           │   │   ├── ThemesFragment.java
    │           │   │           │   │   └── WebFragments.java
    │           │   │           │   ├── model/
    │           │   │           │   │   ├── BaseStore.java
    │           │   │           │   │   ├── FontInfo.java
    │           │   │           │   │   ├── IconInfo.java
    │           │   │           │   │   ├── IconStore.java
    │           │   │           │   │   ├── PluginDoc.java
    │           │   │           │   │   ├── PluginItem.java
    │           │   │           │   │   ├── PluginSetupActionData.java
    │           │   │           │   │   ├── ThemeItem.java
    │           │   │           │   │   └── WebStore.java
    │           │   │           │   ├── notification/
    │           │   │           │   │   ├── PluginCheckLifecycle.java
    │           │   │           │   │   ├── PluginCheckReceiver.java
    │           │   │           │   │   ├── PluginCheckService.java
    │           │   │           │   │   └── PluginNotifier.java
    │           │   │           │   ├── sheet/
    │           │   │           │   │   ├── PluginDetailSheet.java
    │           │   │           │   │   ├── ThemePreviewSheet.java
    │           │   │           │   │   └── WebProjectPreviewSheet.java
    │           │   │           │   └── viewmodel/
    │           │   │           │       ├── FontsViewModel.java
    │           │   │           │       ├── IconsViewModel.java
    │           │   │           │       ├── PluginStoreViewModel.java
    │           │   │           │       ├── ThemesViewModel.java
    │           │   │           │       └── WebStoreViewModel.java
    │           │   │           ├── translate/
    │           │   │           │   ├── TranslateLanguages.java
    │           │   │           │   └── TranslateTask.java
    │           │   │           ├── ui/
    │           │   │           │   └── ProfileView.java
    │           │   │           ├── utils/
    │           │   │           │   ├── AndroidUtils.java
    │           │   │           │   ├── ComponentsPrefs.java
    │           │   │           │   ├── GlassColors.java
    │           │   │           │   ├── ParticleItemAnimator.java
    │           │   │           │   ├── RoundedCornersTransformation.java
    │           │   │           │   └── SoftInputLifeCycleObserver.java
    │           │   │           └── views/
    │           │   │               ├── ButtonProgress.java
    │           │   │               ├── EmptyView.java
    │           │   │               ├── GhostToast.java
    │           │   │               ├── GradientFrameLayout.java
    │           │   │               ├── ImageViewAnimator.java
    │           │   │               ├── ItemGapDecoration.java
    │           │   │               ├── PluginInstallButton.java
    │           │   │               ├── RoundedProgressFill.java
    │           │   │               ├── SegmentedAvatarView.java
    │           │   │               ├── SyncBar.java
    │           │   │               ├── SwipeBackLayout.java
    │           │   │               └── TouchableWebView.java
    │           │   └── jp/
    │           │       └── wasabeef/
    │           │           └── blurry/
    │           │               ├── Blur.java
    │           │               ├── BlurFactor.java
    │           │               ├── Blurry.java
    │           │               ├── BlurTask.java
    │           │               └── Helper.java
    │           └── res/
    │               ├── drawable/
    │               │   ├── bg_copy_button.xml
    │               │   ├── bg_drag_handle.xml
    │               │   ├── bg_shape.xml
    │               │   ├── bg_store_icon_circle.xml
    │               │   ├── ic_arrow_back.xml
    │               │   ├── ic_chevron_left.xml
    │               │   ├── ic_chevron_right.xml
    │               │   ├── ic_close_24.xml
    │               │   ├── ic_copy_24px.xml
    │               │   ├── ic_download.xml
    │               │   ├── ic_empty_state.xml
    │               │   ├── ic_expand_more_24.xml
    │               │   ├── ic_filter_list_24.xml
    │               │   ├── ic_more_vert_24.xml
    │               │   ├── ic_outline_extension.xml
    │               │   ├── ic_outline_font.xml
    │               │   ├── ic_outline_grid.xml
    │               │   ├── ic_outline_palette.xml
    │               │   ├── ic_outline_project.xml
    │               │   ├── ic_public_24.xml
    │               │   ├── ic_search_color.xml
    │               │   ├── ic_sync.xml
    │               │   ├── more_vert.xml
    │               │   ├── outline_search24.xml
    │               │   └── shadow_gradient_hard_bottom_top.xml
    │               ├── layout/
    │               │   ├── activity_store.xml
    │               │   ├── base_blur_bottom_sheet.xml
    │               │   ├── bottom_sheet_color_picker.xml
    │               │   ├── bottom_sheet_exclude_search.xml
    │               │   ├── bottom_sheet_ftp_browser.xml
    │               │   ├── bottom_sheet_ftp_connect.xml
    │               │   ├── bottom_sheet_search.xml
    │               │   ├── bottom_sheet_webview.xml
    │               │   ├── dialog_rename.xml
    │               │   ├── fragment_fonts.xml
    │               │   ├── fragment_icons.xml
    │               │   ├── fragment_plugin_store.xml
    │               │   ├── fragment_store_section.xml
    │               │   ├── fragment_themes.xml
    │               │   ├── fragment_web.xml
    │               │   ├── item_color_card.xml
    │               │   ├── item_font.xml
    │               │   ├── item_ftp_file.xml
    │               │   ├── item_icon.xml
    │               │   ├── item_plugin.xml
    │               │   ├── item_search_content_match.xml
    │               │   ├── item_search_result.xml
    │               │   ├── item_theme.xml
    │               │   ├── item_theme_preview.xml
    │               │   ├── layout_customsheet_rv.xml
    │               │   ├── layout_itemsheet_custom.xml
    │               │   ├── layout_sync.xml
    │               │   ├── layout_webstore.xml
    │               │   ├── search_layout.xml
    │               │   ├── sheet_plugin_detail.xml
    │               │   ├── sheet_plugin_setup.xml
    │               │   ├── sheet_theme_preview.xml
    │               │   ├── sheet_web_preview.xml
    │               │   ├── slider_sheet.xml
    │               │   ├── theme_preview_layoutcontent.xml
    │               │   └── view_empty_state.xml
    │               ├── menu/
    │               │   └── bottom_nav_menu.xml
    │               ├── raw/
    │               │   ├── superripple_effect.agsl
    │               │   ├── thanos_fragment.glsl
    │               │   └── thanos_vertex.glsl
    │               ├── values/
    │               │   ├── attrs.xml
    │               │   ├── strings.xml
    │               │   └── styles.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── editor/
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── assets/
    │           │   └── textmate/
    │           │       ├── languages.json
    │           │       ├── asm/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── asm.tmLanguage.json
    │           │       ├── bat/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── batchfile.tmLanguage.json
    │           │       ├── cmake/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── cmake.tmLanguage.json
    │           │       ├── coq/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── coq.tmLanguage.json
    │           │       ├── cpp/
    │           │       │   └── language-configuration.json
    │           │       ├── csharp/
    │           │       │   └── language-configuration.json
    │           │       ├── css/
    │           │       │   └── language-configuration.json
    │           │       ├── dart/
    │           │       │   ├── Dart.config.json
    │           │       │   └── Dart.tmLanguage.json
    │           │       ├── diff/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── diff.tmLanguage.json
    │           │       ├── go/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── go.tmLanguage.json
    │           │       ├── groovy/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── groovy.tmLanguage.json
    │           │       ├── haskell/
    │           │       │   └── syntaxes/
    │           │       │       └── haskell.tmLanguage.json
    │           │       ├── html/
    │           │       │   └── language-configuration.json
    │           │       ├── htmx/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── htmx.tmLanguage.json
    │           │       ├── ignore/
    │           │       │   ├── ignore.language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── ignore.tmLanguage.json
    │           │       ├── ini/
    │           │       │   ├── ini.language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── ini.tmLanguage.json
    │           │       ├── java/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── java.tmLanguage.json
    │           │       ├── javascript/
    │           │       │   └── language-configuration.json
    │           │       ├── json/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── JSON.tmLanguage.json
    │           │       ├── kotlin/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── kotlin.tmLanguage.json
    │           │       ├── latex/
    │           │       │   ├── language-configuration.json
    │           │       │   └── latex.tmLanguage.json
    │           │       ├── less/
    │           │       │   └── language-configuration.json
    │           │       ├── lisp/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── lisp.tmLanguage.json
    │           │       ├── log/
    │           │       │   └── syntaxes/
    │           │       │       └── log.tmLanguage.json
    │           │       ├── lua/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── lua.tmLanguage.json
    │           │       ├── markdown/
    │           │       │   └── language-configuration.json
    │           │       ├── nim/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── Nim.tmLanguage
    │           │       ├── nix/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── nix.tmLanguage.json
    │           │       ├── pascal/
    │           │       │   ├── pascal.config.json
    │           │       │   └── Pascal.tmLanguage.json
    │           │       ├── perl/
    │           │       │   └── language-configuration.json
    │           │       ├── php/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── html.tmLanguage.json
    │           │       ├── powershell/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── powershell.tmLanguage.json
    │           │       ├── properties/
    │           │       │   ├── properties.language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── properties.tmLanguage.json
    │           │       ├── python/
    │           │       │   └── language-configuration.json
    │           │       ├── r/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── r.tmLanguage.json
    │           │       ├── ruby/
    │           │       │   └── language-configuration.json
    │           │       ├── rust/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── rust.tmLanguage.json
    │           │       ├── scss/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── scss.tmLanguage.json
    │           │       ├── shellscript/
    │           │       │   └── language-configuration.json
    │           │       ├── smali/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── smali.tmLanguage.json
    │           │       ├── sql/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── sql.tmLanguage.json
    │           │       ├── swift/
    │           │       │   └── language-configuration.json
    │           │       ├── text/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── text.tmLanguage.json
    │           │       ├── toml/
    │           │       │   ├── toml.language-configuration.json
    │           │       │   └── toml.tmLanguage.json
    │           │       ├── typescript/
    │           │       │   └── typescript.language-configuration.json
    │           │       ├── xml/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── xml.tmLanguage.json
    │           │       ├── yaml/
    │           │       │   ├── language-configuration.json
    │           │       │   └── syntaxes/
    │           │       │       └── yaml.tmLanguage.json
    │           │       └── zig/
    │           │           ├── Zig.config.json
    │           │           └── Zig.tmLanguage.json
    │           ├── java/
    │           │   ├── com/
    │           │   │   └── moandjiezana/
    │           │   │       └── toml/
    │           │   │           ├── ArrayValueReader.java
    │           │   │           ├── ArrayValueWriter.java
    │           │   │           ├── BooleanValueReaderWriter.java
    │           │   │           ├── Container.java
    │           │   │           ├── Context.java
    │           │   │           ├── DatePolicy.java
    │           │   │           ├── DateValueReaderWriter.java
    │           │   │           ├── Identifier.java
    │           │   │           ├── IdentifierConverter.java
    │           │   │           ├── IndentationPolicy.java
    │           │   │           ├── InlineTableValueReader.java
    │           │   │           ├── Keys.java
    │           │   │           ├── LiteralStringValueReader.java
    │           │   │           ├── MapValueWriter.java
    │           │   │           ├── MultilineLiteralStringValueReader.java
    │           │   │           ├── MultilineStringValueReader.java
    │           │   │           ├── NumberValueReaderWriter.java
    │           │   │           ├── ObjectValueWriter.java
    │           │   │           ├── PrimitiveArrayValueWriter.java
    │           │   │           ├── Results.java
    │           │   │           ├── StringValueReaderWriter.java
    │           │   │           ├── TableArrayValueWriter.java
    │           │   │           ├── Toml.java
    │           │   │           ├── TomlParser.java
    │           │   │           ├── TomlWriter.java
    │           │   │           ├── ValueReader.java
    │           │   │           ├── ValueReaders.java
    │           │   │           ├── ValueWriter.java
    │           │   │           ├── ValueWriters.java
    │           │   │           └── WriterContext.java
    │           │   ├── io/
    │           │   │   └── github/
    │           │   │       └── rosemoe/
    │           │   │           └── sora/
    │           │   │               ├── graphics/
    │           │   │               │   └── inlayHint/
    │           │   │               │       └── GhostTextInlayHintRenderer.java
    │           │   │               ├── lang/
    │           │   │               │   └── styling/
    │           │   │               │       └── inlayHint/
    │           │   │               │           └── GhostTextInlayHint.java
    │           │   │               └── langs/
    │           │   │                   └── textmate/
    │           │   │                       ├── RainbowTextMateAnalyzer.java
    │           │   │                       └── RainbowTextMateLanguage.java
    │           │   └── ir/
    │           │       └── hanzodev1375/
    │           │           └── ghostide/
    │           │               └── codeeditors/
    │           │                   ├── IdeEditor.java
    │           │                   ├── OnDrawCanvas.java
    │           │                   ├── colorrender/
    │           │                   │   ├── ColorInlayUtils.java
    │           │                   │   ├── ColorPickerBottomSheetDialog.java
    │           │                   │   ├── ColorSpectrumView.java
    │           │                   │   ├── HueSliderView.java
    │           │                   │   ├── WebColorIde.java
    │           │                   │   └── model/
    │           │                   │       ├── ColorNameItem.java
    │           │                   │       └── colorrepo/
    │           │                   │           └── ColorNameRepository.java
    │           │                   ├── colorscheme/
    │           │                   │   ├── GhostColorScheme.java
    │           │                   │   └── GhostTextMateTheme.java
    │           │                   ├── dependencychecker/
    │           │                   │   ├── DependencyCheckerIde.java
    │           │                   │   ├── DependencyMatch.java
    │           │                   │   ├── DependencyRefParser.java
    │           │                   │   ├── GradleDependencyCheckerIde.java
    │           │                   │   ├── MavenVersionChecker.java
    │           │                   │   └── TomlDependencyCheckerIde.java
    │           │                   ├── langs/
    │           │                   │   ├── antlr/
    │           │                   │   │   ├── AntlrIncrementalAnalyzeManager.java
    │           │                   │   │   ├── AntlrLanguage.java
    │           │                   │   │   ├── AntlrQuoteHandler.java
    │           │                   │   │   ├── AntlrState.java
    │           │                   │   │   ├── AntlrTextTokenizer.java
    │           │                   │   │   └── AntlrTokens.java
    │           │                   │   ├── antlr4base/
    │           │                   │   │   ├── CharParser.java
    │           │                   │   │   └── SnippetCompletionItem.java
    │           │                   │   ├── asm/
    │           │                   │   │   ├── AsmIncrementalAnalyzeManager.java
    │           │                   │   │   ├── AsmLanguage.java
    │           │                   │   │   ├── AsmQuoteHandler.java
    │           │                   │   │   ├── AsmState.java
    │           │                   │   │   ├── AsmTextTokenizer.java
    │           │                   │   │   └── AsmTokens.java
    │           │                   │   ├── c/
    │           │                   │   │   ├── CIncrementalAnalyzeManager.java
    │           │                   │   │   ├── CLanguage.java
    │           │                   │   │   ├── CQuoteHandler.java
    │           │                   │   │   ├── CState.java
    │           │                   │   │   ├── CTextTokenizer.java
    │           │                   │   │   └── CTokens.java
    │           │                   │   ├── cpp/
    │           │                   │   │   ├── CppFormatter.java
    │           │                   │   │   ├── CppIncrementalAnalyzeManager.java
    │           │                   │   │   ├── CppLanguage.java
    │           │                   │   │   ├── CppQuoteHandler.java
    │           │                   │   │   ├── CppState.java
    │           │                   │   │   ├── CppTextTokenizer.java
    │           │                   │   │   └── CppTokens.java
    │           │                   │   ├── csharp/
    │           │                   │   │   ├── CSharpIncrementalAnalyzeManager.java
    │           │                   │   │   ├── CSharpLanguage.java
    │           │                   │   │   ├── CSharpQuoteHandler.java
    │           │                   │   │   ├── CSharpState.java
    │           │                   │   │   ├── CSharpTextTokenizer.java
    │           │                   │   │   └── CSharpTokens.java
    │           │                   │   ├── css/
    │           │                   │   │   └── CssLanguage.java
    │           │                   │   ├── dart/
    │           │                   │   │   ├── DartIncrementalAnalyzeManager.java
    │           │                   │   │   ├── DartLanguage.java
    │           │                   │   │   ├── DartQuoteHandler.java
    │           │                   │   │   ├── DartState.java
    │           │                   │   │   ├── DartTextTokenizer.java
    │           │                   │   │   └── DartTokens.java
    │           │                   │   ├── elixir/
    │           │                   │   │   └── ElixirLanguage.java
    │           │                   │   ├── formatHelp/
    │           │                   │   │   ├── DebianBootstrap.java
    │           │                   │   │   └── PrettierFormatter.java
    │           │                   │   ├── go/
    │           │                   │   │   ├── GoIncrementalAnalyzeManager.java
    │           │                   │   │   ├── GoLanguage.java
    │           │                   │   │   ├── GoQuoteHandler.java
    │           │                   │   │   ├── GoState.java
    │           │                   │   │   ├── GoTextTokenizer.java
    │           │                   │   │   └── GoTokens.java
    │           │                   │   ├── gradle/
    │           │                   │   │   ├── GradleIncrementalAnalyzeManager.java
    │           │                   │   │   ├── GradleLanguage.java
    │           │                   │   │   ├── GradleQuoteHandler.java
    │           │                   │   │   ├── GradleTextTokenizer.java
    │           │                   │   │   ├── State.java
    │           │                   │   │   └── Tokens.java
    │           │                   │   ├── haskell/
    │           │                   │   │   └── HaskellLanguage.java
    │           │                   │   ├── html/
    │           │                   │   │   ├── HtmlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── HtmlLanguage.java
    │           │                   │   │   ├── HtmlState.java
    │           │                   │   │   ├── HtmlTextTokenizer.java
    │           │                   │   │   └── HtmlTokens.java
    │           │                   │   ├── ini/
    │           │                   │   │   ├── IniIncrementalAnalyzeManager.java
    │           │                   │   │   ├── IniLanguage.java
    │           │                   │   │   ├── IniQuoteHandler.java
    │           │                   │   │   ├── IniState.java
    │           │                   │   │   ├── IniTextTokenizer.java
    │           │                   │   │   └── IniTokens.java
    │           │                   │   ├── java/
    │           │                   │   │   ├── JavaCodeFormatter.java
    │           │                   │   │   ├── JavaIncrementalAnalyzeManager.java
    │           │                   │   │   ├── JavaLanguage.java
    │           │                   │   │   ├── JavaQuoteHandler.java
    │           │                   │   │   ├── JavaTextTokenizer.java
    │           │                   │   │   ├── State.java
    │           │                   │   │   └── Tokens.java
    │           │                   │   ├── js/
    │           │                   │   │   └── JsLanguage.java
    │           │                   │   ├── json/
    │           │                   │   │   ├── JsonIncrementalAnalyzeManager.java
    │           │                   │   │   ├── JsonLanguage.java
    │           │                   │   │   ├── JsonState.java
    │           │                   │   │   ├── JsonTextTokenizer.java
    │           │                   │   │   └── JsonTokens.java
    │           │                   │   ├── julia/
    │           │                   │   │   └── JuliaLanguage.java
    │           │                   │   ├── kotlin/
    │           │                   │   │   ├── KotlinIncrementalAnalyzeManager.java
    │           │                   │   │   ├── KotlinLanguage.java
    │           │                   │   │   ├── KotlinQuoteHandler.java
    │           │                   │   │   ├── KotlinTextTokenizer.java
    │           │                   │   │   ├── KtFormater.java
    │           │                   │   │   ├── State.java
    │           │                   │   │   └── Tokens.java
    │           │                   │   ├── lsp/
    │           │                   │   │   ├── AndroidClasspathResolver.java
    │           │                   │   │   ├── EditorLanguageFactory.java
    │           │                   │   │   ├── EmmetServer.java
    │           │                   │   │   ├── GthServer.java
    │           │                   │   │   ├── HtmlServer.java
    │           │                   │   │   ├── LspContentImpl.java
    │           │                   │   │   ├── LspExtensionBridge.java
    │           │                   │   │   ├── LspHoverHighlighter.java
    │           │                   │   │   ├── LspInitParamsHook.java
    │           │                   │   │   ├── LspRouter.java
    │           │                   │   │   ├── LspServerConnectionStreamAdapter.java
    │           │                   │   │   ├── LspUriBridge.kt
    │           │                   │   │   ├── ProotProcessLauncherImpl.java
    │           │                   │   │   ├── ProotStdioConnectionProvider.java
    │           │                   │   │   ├── gth/
    │           │                   │   │   │   ├── EmbeddedGthConnectionProvider.java
    │           │                   │   │   │   ├── GhostThemeLanguageServer.java
    │           │                   │   │   │   ├── GthDiagnosticEngine.java
    │           │                   │   │   │   ├── GthDocument.java
    │           │                   │   │   │   ├── GthScanner.java
    │           │                   │   │   │   ├── GthToken.java
    │           │                   │   │   │   └── ThemeSchema.java
    │           │                   │   │   ├── listener/
    │           │                   │   │   │   └── GhostLspStatusListener.java
    │           │                   │   │   └── model/
    │           │                   │   │       ├── BreadcrumbItem.java
    │           │                   │   │       └── OutlineSymbol.java
    │           │                   │   ├── lua/
    │           │                   │   │   ├── LuaIncrementalAnalyzeManager.java
    │           │                   │   │   ├── LuaLanguage.java
    │           │                   │   │   ├── LuaQuoteHandler.java
    │           │                   │   │   ├── LuaState.java
    │           │                   │   │   ├── LuaTextTokenizer.java
    │           │                   │   │   └── LuaTokens.java
    │           │                   │   ├── markdown/
    │           │                   │   │   ├── MarkdownIncrementalAnalyzeManager.java
    │           │                   │   │   ├── MarkdownLanguage.java
    │           │                   │   │   ├── MarkdownQuoteHandler.java
    │           │                   │   │   ├── MarkdownTextTokenizer.java
    │           │                   │   │   ├── State.java
    │           │                   │   │   └── Tokens.java
    │           │                   │   ├── nim/
    │           │                   │   │   └── NimLanguage.java
    │           │                   │   ├── perl/
    │           │                   │   │   ├── PerlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── PerlLanguage.java
    │           │                   │   │   ├── PerlQuoteHandler.java
    │           │                   │   │   ├── PerlState.java
    │           │                   │   │   ├── PerlTextTokenizer.java
    │           │                   │   │   └── PerlTokens.java
    │           │                   │   ├── php/
    │           │                   │   │   ├── PhpFormatter.java
    │           │                   │   │   ├── PhpIncrementalAnalyzeManager.java
    │           │                   │   │   ├── PhpLanguage.java
    │           │                   │   │   ├── PhpQuoteHandler.java
    │           │                   │   │   ├── PhpState.java
    │           │                   │   │   ├── PhpTextTokenizer.java
    │           │                   │   │   └── PhpTokens.java
    │           │                   │   ├── python3/
    │           │                   │   │   ├── Python3Language.java
    │           │                   │   │   ├── PythonFormatter.java
    │           │                   │   │   ├── PythonIncrementalAnalyzeManager.java
    │           │                   │   │   ├── PythonState.java
    │           │                   │   │   ├── PythonTextTokenizer.java
    │           │                   │   │   └── PythonTokens.java
    │           │                   │   ├── r/
    │           │                   │   │   └── RLanguage.java
    │           │                   │   ├── ruby/
    │           │                   │   │   ├── RubyFormatter.java
    │           │                   │   │   ├── RubyIncrementalAnalyzeManager.java
    │           │                   │   │   ├── RubyLanguage.java
    │           │                   │   │   ├── RubyQuoteHandler.java
    │           │                   │   │   ├── RubyState.java
    │           │                   │   │   ├── RubyTextTokenizer.java
    │           │                   │   │   └── RubyTokens.java
    │           │                   │   ├── rust/
    │           │                   │   │   ├── RustIncrementalAnalyzeManager.java
    │           │                   │   │   ├── RustLanguage.java
    │           │                   │   │   ├── RustQuoteHandler.java
    │           │                   │   │   ├── RustState.java
    │           │                   │   │   ├── RustTextTokenizer.java
    │           │                   │   │   └── RustTokens.java
    │           │                   │   ├── sass/
    │           │                   │   │   ├── SassIncrementalAnalyzeManager.java
    │           │                   │   │   ├── SassLanguage.java
    │           │                   │   │   ├── SassQuoteHandler.java
    │           │                   │   │   ├── SassState.java
    │           │                   │   │   ├── SassTextTokenizer.java
    │           │                   │   │   └── SassTokens.java
    │           │                   │   ├── scala/
    │           │                   │   │   └── ScalaLanguage.java
    │           │                   │   ├── shell/
    │           │                   │   │   ├── ShellIncrementalAnalyzeManager.java
    │           │                   │   │   ├── ShellLanguage.java
    │           │                   │   │   ├── ShellQuoteHandler.java
    │           │                   │   │   ├── ShellState.java
    │           │                   │   │   ├── ShellTextTokenizer.java
    │           │                   │   │   └── ShellTokens.java
    │           │                   │   ├── simple/
    │           │                   │   │   ├── SimpleIncrementalAnalyzeManager.java
    │           │                   │   │   ├── SimpleLangConfig.java
    │           │                   │   │   ├── SimpleLanguage.java
    │           │                   │   │   ├── SimpleQuoteHandler.java
    │           │                   │   │   ├── SimpleState.java
    │           │                   │   │   ├── SimpleTextTokenizer.java
    │           │                   │   │   └── SimpleTokens.java
    │           │                   │   ├── solidity/
    │           │                   │   │   └── SolidityLanguage.java
    │           │                   │   ├── sql/
    │           │                   │   │   ├── SqlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── SqlLanguage.java
    │           │                   │   │   ├── SqlQuoteHandler.java
    │           │                   │   │   ├── SqlState.java
    │           │                   │   │   ├── SqlTextTokenizer.java
    │           │                   │   │   └── SqlTokens.java
    │           │                   │   ├── swift/
    │           │                   │   │   ├── SwiftIncrementalAnalyzeManager.java
    │           │                   │   │   ├── SwiftLanguage.java
    │           │                   │   │   ├── SwiftQuoteHandler.java
    │           │                   │   │   ├── SwiftState.java
    │           │                   │   │   ├── SwiftTextTokenizer.java
    │           │                   │   │   └── SwiftTokens.java
    │           │                   │   ├── toml/
    │           │                   │   │   ├── State.java
    │           │                   │   │   ├── Tokens.java
    │           │                   │   │   ├── TomlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── TomlLanguage.java
    │           │                   │   │   ├── TomlQuoteHandler.java
    │           │                   │   │   └── TomlTextTokenizer.java
    │           │                   │   ├── tsx/
    │           │                   │   │   ├── TsxIncrementalAnalyzeManager.java
    │           │                   │   │   ├── TsxLanguage.java
    │           │                   │   │   ├── TsxQuoteHandler.java
    │           │                   │   │   ├── TsxState.java
    │           │                   │   │   ├── TsxTextTokenizer.java
    │           │                   │   │   └── TsxTokens.java
    │           │                   │   ├── vue/
    │           │                   │   │   ├── VueIncrementalAnalyzeManager.java
    │           │                   │   │   ├── VueLanguage.java
    │           │                   │   │   ├── VueQuoteHandler.java
    │           │                   │   │   ├── VueState.java
    │           │                   │   │   ├── VueTextTokenizer.java
    │           │                   │   │   └── VueTokens.java
    │           │                   │   ├── xml/
    │           │                   │   │   ├── XmlFormatter.java
    │           │                   │   │   ├── XmlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── XmlLanguage.java
    │           │                   │   │   ├── XmlQuoteHandler.java
    │           │                   │   │   ├── XmlState.java
    │           │                   │   │   ├── XmlTextTokenizer.java
    │           │                   │   │   └── XmlTokens.java
    │           │                   │   ├── yaml/
    │           │                   │   │   ├── State.java
    │           │                   │   │   ├── Tokens.java
    │           │                   │   │   ├── YamlIncrementalAnalyzeManager.java
    │           │                   │   │   ├── YamlLanguage.java
    │           │                   │   │   ├── YamlQuoteHandler.java
    │           │                   │   │   └── YamlTextTokenizer.java
    │           │                   │   └── zig/
    │           │                   │       ├── ZigIncrementalAnalyzeManager.java
    │           │                   │       ├── ZigLanguage.java
    │           │                   │       ├── ZigQuoteHandler.java
    │           │                   │       ├── ZigState.java
    │           │                   │       ├── ZigTextTokenizer.java
    │           │                   │       └── ZigTokens.java
    │           │                   ├── lspcustomhot/
    │           │                   │   ├── Css3Server.java
    │           │                   │   ├── CustomCompletionItem.java
    │           │                   │   ├── EmmetParser.java
    │           │                   │   ├── KeyWordConst.java
    │           │                   │   ├── PathCompleter.java
    │           │                   │   ├── PseudoData.java
    │           │                   │   ├── VFSManager.java
    │           │                   │   └── model/
    │           │                   │       └── CssSelect.java
    │           │                   ├── preview/
    │           │                   │   ├── EditorPopUp.java
    │           │                   │   ├── ImagePreviewIde.java
    │           │                   │   ├── ImageRefUtils.java
    │           │                   │   ├── Match.java
    │           │                   │   ├── url/
    │           │                   │   │   ├── OnLinkClickEventListener.java
    │           │                   │   │   ├── UrlPreviewIde.java
    │           │                   │   │   └── UrlRefUtils.java
    │           │                   │   └── xmlattr/
    │           │                   │       ├── XmlAttrDoc.java
    │           │                   │       ├── XmlAttrDocRepository.java
    │           │                   │       ├── XmlAttrPreviewIde.java
    │           │                   │       └── XmlAttrRefUtils.java
    │           │                   ├── rainbow/
    │           │                   │   ├── BracketSyntax.java
    │           │                   │   ├── BracketSyntaxLoader.java
    │           │                   │   ├── RainbowBracketHighlighter.java
    │           │                   │   ├── RainbowBracketRegistry.java
    │           │                   │   └── RainbowBracketScanner.java
    │           │                   ├── setting/
    │           │                   │   ├── Constants.java
    │           │                   │   └── PreferencesUtils.java
    │           │                   ├── shortcut/
    │           │                   │   ├── ShortcutAction.java
    │           │                   │   ├── ShortcutKey.java
    │           │                   │   └── ShortcutManager.java
    │           │                   ├── snippets/
    │           │                   │   ├── SnippetScopeUtil.java
    │           │                   │   ├── UserSnippet.java
    │           │                   │   ├── UserSnippetCompletion.java
    │           │                   │   └── UserSnippetProvider.java
    │           │                   ├── stringres/
    │           │                   │   ├── ExtractStringResourceDialog.java
    │           │                   │   └── StringResourceExtractorIde.java
    │           │                   ├── style/
    │           │                   │   └── BitmapHandleStyle.java
    │           │                   ├── textmate/
    │           │                   │   ├── TextMateGrammars.java
    │           │                   │   ├── TextMateLanguages.java
    │           │                   │   └── TextMateScopeMap.java
    │           │                   ├── ui/
    │           │                   │   ├── CodeRunnerActions.java
    │           │                   │   ├── ColorSwatchDrawable.java
    │           │                   │   ├── CustomCircleDrawable.java
    │           │                   │   ├── CustomCompletionItem.java
    │           │                   │   ├── CustomCompletionLayout.java
    │           │                   │   ├── CustomEditorAutoCompletion.java
    │           │                   │   ├── CustomEditorCompletionAdapter.java
    │           │                   │   ├── CustomEditorTextActionWindow.java
    │           │                   │   ├── EditorContextMenu.java
    │           │                   │   ├── EditorGlassMenu.java
    │           │                   │   ├── GhostDiagnosticTooltipLayout.java
    │           │                   │   ├── GhostTextCompletionManager.java
    │           │                   │   └── model/
    │           │                   │       └── OpenFileLocationEvent.java
    │           │                   └── util/
    │           │                       ├── CustomFormatter.java
    │           │                       └── formatter/
    │           │                           ├── BaseFormatter.java
    │           │                           ├── CssFormatter.java
    │           │                           └── JsFormatter.java
    │           └── res/
    │               ├── drawable/
    │               │   ├── contextual_magnifier_background.xml
    │               │   ├── ic_copy_selection.xml
    │               │   ├── ic_cut_selection.xml
    │               │   ├── ic_expand_selection.xml
    │               │   ├── ic_format_selection.xml
    │               │   ├── ic_lsp_code_action.xml
    │               │   ├── ic_lsp_definition.xml
    │               │   ├── ic_lsp_references.xml
    │               │   ├── ic_lsp_rename.xml
    │               │   ├── ic_paste_content.xml
    │               │   ├── ic_select_all.xml
    │               │   ├── ic_select_drag.xml
    │               │   └── ic_translate.xml
    │               ├── layout/
    │               │   ├── color_picker_bottom_sheet.xml
    │               │   ├── contextual_text_compose_panel.xml
    │               │   ├── dialog_extract_string_resource.xml
    │               │   ├── editor_completion_result_item.xml
    │               │   ├── editor_html_tag_preview.xml
    │               │   ├── editor_progress_indicator_layout.xml
    │               │   ├── editor_xml_attr_preview.xml
    │               │   └── ghost_diagnostic_tooltip_window.xml
    │               ├── values/
    │               │   └── strings.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── gradle/
    │   ├── libs.versions.toml
    │   └── wrapper/
    │       └── gradle-wrapper.properties
    ├── ide-api/
    │   └── src/
    │       └── main/
    │           └── java/
    │               └── ir/
    │                   └── hanzodev1375/
    │                       └── ghostide/
    │                           └── ide/
    │                               └── api/
    │                                   ├── EditorExtensionPoints.java
    │                                   ├── LspServerConnection.java
    │                                   ├── LspServerConnectionFactory.java
    │                                   ├── LspServerDefinition.java
    │                                   ├── LspServerProvider.java
    │                                   └── LspServerRequest.java
    ├── ide-ui-api/
    │   ├── consumer-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           └── java/
    │               └── ir/
    │                   └── hanzodev1375/
    │                       └── ghostide/
    │                           └── ide/
    │                               └── ui/
    │                                   └── api/
    │                                       ├── CodeRunnerHost.java
    │                                       ├── EditorActionHandler.java
    │                                       ├── EditorHost.java
    │                                       ├── EditorPanel.java
    │                                       ├── FileClickContext.java
    │                                       ├── FileClickHandler.java
    │                                       ├── FileClickHandlers.java
    │                                       ├── FileEvent.java
    │                                       ├── FileEventListener.java
    │                                       ├── FileIconContributor.java
    │                                       ├── FileManagerHost.java
    │                                       ├── FilePanel.java
    │                                       ├── IdeEvents.java
    │                                       ├── IdeHostServices.java
    │                                       ├── JsonFileIconContributor.java
    │                                       ├── Panel.java
    │                                       ├── PanelHostService.java
    │                                       ├── PanelStateStore.java
    │                                       ├── PluginIcons.java
    │                                       ├── PluginBackgroundRenderer.java
    │                                       ├── PluginIconState.java
    │                                       ├── PluginScreen.java
    │                                       ├── PluginStateMod.java
    │                                       ├── PluginToolbarIcon.java
    │                                       ├── PluginUiExtensionPoints.java
    │                                       ├── ProotProcessLauncher.java
    │                                       ├── SyncHost.java
    │                                       ├── SyncSession.java
    │                                       └── UiFeedbackHost.java
    ├── jgit/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── ir/
    │           │       └── hanzodev1375/
    │           │           └── ghostide/
    │           │               └── jgit/
    │           │                   ├── GitHubClient.java
    │           │                   ├── GitHubProfileSheet.java
    │           │                   ├── adapter/
    │           │                   │   ├── BlameAdapter.java
    │           │                   │   ├── BranchAdapter.java
    │           │                   │   ├── CommitAdapter.java
    │           │                   │   ├── EventAdapter.java
    │           │                   │   ├── FileChangeAdapter.java
    │           │                   │   ├── RemoteAdapter.java
    │           │                   │   ├── RepoAdapter.java
    │           │                   │   ├── StashAdapter.java
    │           │                   │   ├── SubmoduleAdapter.java
    │           │                   │   ├── TagAdapter.java
    │           │                   │   └── ViewPagerAdapter.java
    │           │                   ├── dialogs/
    │           │                   │   ├── AddRemoteDialog.java
    │           │                   │   └── CommitDialog.java
    │           │                   ├── diff/
    │           │                   │   ├── DiffLine.java
    │           │                   │   ├── DiffTheme.java
    │           │                   │   ├── GitDiffViewer.java
    │           │                   │   ├── MultiLanguageHighlighter.java
    │           │                   │   ├── RainbowBracketHighlighter.java
    │           │                   │   └── SyntaxHighlighter.java
    │           │                   ├── fragments/
    │           │                   │   ├── BlameFragment.java
    │           │                   │   ├── BranchesFragment.java
    │           │                   │   ├── ChangedFilesFragment.java
    │           │                   │   ├── CommitDiffBottomSheetFragment.java
    │           │                   │   ├── CommitHistoryFragment.java
    │           │                   │   ├── ConflictResolverFragment.java
    │           │                   │   ├── DiffViewerFragment.java
    │           │                   │   ├── GitBottomSheetFragment.java
    │           │                   │   ├── GitignoreFragment.java
    │           │                   │   ├── GitInitSheet.java
    │           │                   │   ├── RemotesFragment.java
    │           │                   │   ├── ResetFragment.java
    │           │                   │   ├── StashFragment.java
    │           │                   │   ├── SubmodulesFragment.java
    │           │                   │   └── TagsFragment.java
    │           │                   ├── jgitandroid/
    │           │                   │   ├── ChangeType.java
    │           │                   │   ├── RepositoryStatus.java
    │           │                   │   ├── datamanager/
    │           │                   │   │   ├── GitManager.java
    │           │                   │   │   └── GitViewModel.java
    │           │                   │   └── model/
    │           │                   │       ├── BlameInfo.java
    │           │                   │       ├── CommitInfo.java
    │           │                   │       ├── ConflictFile.java
    │           │                   │       ├── FetchResult.java
    │           │                   │       ├── FileChange.java
    │           │                   │       ├── OperationResult.java
    │           │                   │       ├── PullResult.java
    │           │                   │       ├── PushResult.java
    │           │                   │       ├── RemoteInfo.java
    │           │                   │       ├── RemoteOperationResult.java
    │           │                   │       ├── ResetMode.java
    │           │                   │       ├── StashInfo.java
    │           │                   │       ├── SubmoduleInfo.java
    │           │                   │       └── TagInfo.java
    │           │                   └── model/
    │           │                       ├── GitHubEvent.java
    │           │                       ├── GitHubRepo.java
    │           │                       ├── GitTab.java
    │           │                       └── UserStats.java
    │           └── res/
    │               ├── drawable/
    │               │   ├── add_24px.xml
    │               │   ├── arrow_upload_ready_24px.xml
    │               │   ├── circle_24px.xml
    │               │   ├── delete_24px.xml
    │               │   ├── deployed_code_24px.xml
    │               │   ├── fork_right_24px.xml
    │               │   ├── ic_bolt.xml
    │               │   ├── inbox_24px.xml
    │               │   ├── info_24px.xml
    │               │   ├── issue_24px.xml
    │               │   ├── merge_24px.xml
    │               │   ├── person_24px.xml
    │               │   ├── rss_feed_24px.xml
    │               │   └── star_24px.xml
    │               ├── layout/
    │               │   ├── bottom_sheet_commit_diff.xml
    │               │   ├── bottom_sheet_git.xml
    │               │   ├── bottom_sheet_git_init.xml
    │               │   ├── dialog_add_remote.xml
    │               │   ├── dialog_add_submodule.xml
    │               │   ├── dialog_commit.xml
    │               │   ├── fragment_blame.xml
    │               │   ├── fragment_branches.xml
    │               │   ├── fragment_changed_files.xml
    │               │   ├── fragment_commit_history.xml
    │               │   ├── fragment_conflict_resolver.xml
    │               │   ├── fragment_diff_viewer.xml
    │               │   ├── fragment_github_profile_sheet.xml
    │               │   ├── fragment_gitignore.xml
    │               │   ├── fragment_remotes.xml
    │               │   ├── fragment_reset.xml
    │               │   ├── fragment_stash.xml
    │               │   ├── fragment_submodules.xml
    │               │   ├── fragment_tags.xml
    │               │   ├── git_local_config.xml
    │               │   ├── item_blame.xml
    │               │   ├── item_branch.xml
    │               │   ├── item_commit.xml
    │               │   ├── item_conflict_file.xml
    │               │   ├── item_event.xml
    │               │   ├── item_file_change.xml
    │               │   ├── item_remote.xml
    │               │   ├── item_repo.xml
    │               │   ├── item_stash.xml
    │               │   ├── item_submodule.xml
    │               │   └── item_tag.xml
    │               ├── values/
    │               │   └── strings.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── logcat/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── ir/
    │           │       └── ghostide/
    │           │           └── logcat/
    │           │               ├── BottomSheetLogView.java
    │           │               ├── LogAdapter.java
    │           │               ├── LogcatReader.java
    │           │               ├── LogEntry.java
    │           │               └── MaterialLogCatView.java
    │           └── res/
    │               ├── drawable/
    │               │   └── ic_save_logs.xml
    │               ├── layout/
    │               │   ├── fragment_logview.xml
    │               │   ├── item_log.xml
    │               │   └── view_material_logcat.xml
    │               ├── values/
    │               │   └── strings.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── materialfileicon/
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           └── java/
    │               └── ir/
    │                   └── hanzodev1375/
    │                       └── ghostide/
    │                           └── materialfileicon/
    │                               └── core/
    │                                   ├── CommonUtil.java
    │                                   ├── FileHelper.java
    │                                   ├── FileIconHelper.java
    │                                   ├── FileUtil.java
    │                                   ├── IconInitProvider.java
    │                                   ├── JsonFileIconHelper.java
    │                                   ├── environment/
    │                                   │   ├── AndroidDevDetector.java
    │                                   │   ├── AngularJsDetector.java
    │                                   │   ├── FileEnvironmentHelper.java
    │                                   │   ├── GitDetector.java
    │                                   │   ├── NodejsDetector.java
    │                                   │   ├── ReactDetector.java
    │                                   │   ├── ReadmeDetector.java
    │                                   │   └── VueDetector.java
    │                                   └── langcolor/
    │                                       └── LanguageColors.java
    ├── musicpreview/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── irhanzodev1375/
    │           │       └── musicpreview/
    │           │           ├── ButtonAnim.java
    │           │           ├── ColorPaletteUtils.java
    │           │           ├── MediaPlayerListener.java
    │           │           ├── Music.java
    │           │           ├── MusicPlayerBottomSheetFragment.java
    │           │           ├── MusicView.java
    │           │           ├── PlayerToggle.java
    │           │           └── SquigglyProgress.java
    │           └── res/
    │               ├── anim/
    │               │   ├── music_bottom_pause_to_play_animation.xml
    │               │   ├── music_bottom_play_to_pause_animation.xml
    │               │   ├── music_bottom_sheet_enter.xml
    │               │   ├── music_bottom_sheet_exit.xml
    │               │   ├── music_rotate_90_animation.xml
    │               │   ├── music_rotate_minus_90.xml
    │               │   ├── music_upper_pause_to_play_animation.xml
    │               │   └── music_upper_play_to_pause_animation.xml
    │               ├── drawable/
    │               │   ├── icon_pause_round.xml
    │               │   ├── icon_play_arrow_round.xml
    │               │   ├── icon_skip_next_round.xml
    │               │   ├── icon_skip_previous_round.xml
    │               │   ├── music_drag_handle.xml
    │               │   ├── music_icon_pause.xml
    │               │   ├── music_pause_avd.xml
    │               │   ├── music_resume_avd.xml
    │               │   ├── music_round_square_avd.xml
    │               │   ├── music_scallop_avd.xml
    │               │   └── music_seekbar_thumb.xml
    │               ├── layout/
    │               │   ├── fragment_music_player_bottom_sheet.xml
    │               │   ├── music_layout.xml
    │               │   └── design/
    │               │       └── music_layout.xml
    │               ├── values/
    │               │   ├── dimens_music.xml
    │               │   ├── strings.xml
    │               │   └── styles_music.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── plugin-api/
    │   └── src/
    │       └── main/
    │           └── java/
    │               └── ir/
    │                   └── hanzodev1375/
    │                       └── ghostide/
    │                           └── plugin/
    │                               └── api/
    │                                   ├── ConfigurableExtension.java
    │                                   ├── CoreExtensionPoints.java
    │                                   ├── CoreServices.java
    │                                   ├── DefaultExtensionRegistry.java
    │                                   ├── DefaultServiceRegistry.java
    │                                   ├── Disposable.java
    │                                   ├── ExtensionPoint.java
    │                                   ├── ExtensionRegistration.java
    │                                   ├── ExtensionRegistry.java
    │                                   ├── GhostPlugin.java
    │                                   ├── GlobalRegistry.java
    │                                   ├── LayeredServiceRegistry.java
    │                                   ├── MutableExtensionRegistry.java
    │                                   ├── MutableServiceRegistry.java
    │                                   ├── PluginCommand.java
    │                                   ├── PluginContext.java
    │                                   ├── PluginDependency.java
    │                                   ├── PluginDescriptor.java
    │                                   ├── PluginIds.java
    │                                   ├── PluginLogger.java
    │                                   ├── PluginSetting.java
    │                                   ├── PluginSettingsContributor.java
    │                                   ├── PluginSetupAction.java
    │                                   ├── PluginStorage.java
    │                                   ├── ServiceKey.java
    │                                   └── ServiceRegistry.java
    ├── prddownloader/
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   ├── com/
    │           │   │   └── downloader/
    │           │   │       ├── Constants.java
    │           │   │       ├── Error.java
    │           │   │       ├── OnCancelListener.java
    │           │   │       ├── OnDownloadListener.java
    │           │   │       ├── OnPauseListener.java
    │           │   │       ├── OnProgressListener.java
    │           │   │       ├── OnStartOrResumeListener.java
    │           │   │       ├── PRDownloader.java
    │           │   │       ├── PRDownloaderConfig.java
    │           │   │       ├── Priority.java
    │           │   │       ├── Progress.java
    │           │   │       ├── Response.java
    │           │   │       ├── Status.java
    │           │   │       ├── core/
    │           │   │       │   ├── Core.java
    │           │   │       │   ├── DefaultExecutorSupplier.java
    │           │   │       │   ├── DownloadExecutor.java
    │           │   │       │   ├── DownloadFutureTask.java
    │           │   │       │   ├── ExecutorSupplier.java
    │           │   │       │   ├── MainThreadExecutor.java
    │           │   │       │   └── PriorityThreadFactory.java
    │           │   │       ├── database/
    │           │   │       │   ├── AppDbHelper.java
    │           │   │       │   ├── DatabaseOpenHelper.java
    │           │   │       │   ├── DbHelper.java
    │           │   │       │   ├── DownloadModel.java
    │           │   │       │   └── NoOpsDbHelper.java
    │           │   │       ├── handler/
    │           │   │       │   └── ProgressHandler.java
    │           │   │       ├── httpclient/
    │           │   │       │   ├── DefaultHttpClient.java
    │           │   │       │   └── HttpClient.java
    │           │   │       ├── internal/
    │           │   │       │   ├── ComponentHolder.java
    │           │   │       │   ├── DownloadRequestQueue.java
    │           │   │       │   ├── DownloadRunnable.java
    │           │   │       │   ├── DownloadTask.java
    │           │   │       │   ├── SynchronousCall.java
    │           │   │       │   └── stream/
    │           │   │       │       ├── FileDownloadOutputStream.java
    │           │   │       │       └── FileDownloadRandomAccessFile.java
    │           │   │       ├── request/
    │           │   │       │   ├── DownloadRequest.java
    │           │   │       │   ├── DownloadRequestBuilder.java
    │           │   │       │   └── RequestBuilder.java
    │           │   │       └── utils/
    │           │   │           └── Utils.java
    │           │   └── ninja/
    │           │       └── coder/
    │           │           └── appuploader/
    │           │               └── main/
    │           │                   ├── ApkInstallerCompat.java
    │           │                   ├── ViewDownloder.java
    │           │                   └── appupdate/
    │           │                       ├── AppUpdateCallBack.java
    │           │                       ├── MarkwonHelper.java
    │           │                       ├── UpadteAppView.java
    │           │                       └── model/
    │           │                           └── AppUpdateModel.java
    │           └── res/
    │               ├── drawable/
    │               │   └── round_install_mobile_24.xml
    │               ├── layout/
    │               │   └── layout_downloder_child.xml
    │               ├── values/
    │               │   └── strings.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── proot/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           └── cpp/
    │               ├── arch.h
    │               ├── attribute.h
    │               ├── build.h
    │               ├── CMakeLists.txt
    │               ├── compat.h
    │               ├── cli/
    │               │   ├── cli.c
    │               │   ├── cli.h
    │               │   ├── note.c
    │               │   ├── note.h
    │               │   ├── proot.c
    │               │   └── proot.h
    │               ├── execve/
    │               │   ├── aoxp.c
    │               │   ├── aoxp.h
    │               │   ├── auxv.c
    │               │   ├── auxv.h
    │               │   ├── elf.c
    │               │   ├── elf.h
    │               │   ├── enter.c
    │               │   ├── execve.h
    │               │   ├── exit.c
    │               │   ├── ldso.c
    │               │   ├── ldso.h
    │               │   ├── shebang.c
    │               │   └── shebang.h
    │               ├── extension/
    │               │   ├── extension.c
    │               │   ├── extension.h
    │               │   ├── ashmem_memfd/
    │               │   │   └── ashmem_memfd.c
    │               │   ├── fake_id0/
    │               │   │   ├── access.c
    │               │   │   ├── access.h
    │               │   │   ├── chmod.c
    │               │   │   ├── chmod.h
    │               │   │   ├── chown.c
    │               │   │   ├── chown.h
    │               │   │   ├── chroot.c
    │               │   │   ├── chroot.h
    │               │   │   ├── config.h
    │               │   │   ├── exec.c
    │               │   │   ├── exec.h
    │               │   │   ├── fake_id0.c
    │               │   │   ├── getsockopt.c
    │               │   │   ├── getsockopt.h
    │               │   │   ├── helper_functions.c
    │               │   │   ├── helper_functions.h
    │               │   │   ├── link.c
    │               │   │   ├── link.h
    │               │   │   ├── mk.c
    │               │   │   ├── mk.h
    │               │   │   ├── open.c
    │               │   │   ├── open.h
    │               │   │   ├── rename.c
    │               │   │   ├── rename.h
    │               │   │   ├── sendmsg.c
    │               │   │   ├── sendmsg.h
    │               │   │   ├── socket.c
    │               │   │   ├── socket.h
    │               │   │   ├── stat.c
    │               │   │   ├── stat.h
    │               │   │   ├── symlink.c
    │               │   │   ├── symlink.h
    │               │   │   ├── unlink.c
    │               │   │   ├── unlink.h
    │               │   │   ├── utimensat.c
    │               │   │   └── utimensat.h
    │               │   ├── fix_symlink_size/
    │               │   │   └── fix_symlink_size.c
    │               │   ├── hidden_files/
    │               │   │   └── hidden_files.c
    │               │   ├── kompat/
    │               │   │   └── kompat.c
    │               │   ├── link2symlink/
    │               │   │   └── link2symlink.c
    │               │   ├── mountinfo/
    │               │   │   └── mountinfo.c
    │               │   ├── port_switch/
    │               │   │   └── port_switch.c
    │               │   └── sysvipc/
    │               │       ├── sysvipc.c
    │               │       ├── sysvipc.h
    │               │       ├── sysvipc_internal.h
    │               │       ├── sysvipc_msg.c
    │               │       ├── sysvipc_sem.c
    │               │       ├── sysvipc_shm.c
    │               │       └── sysvipc_sys.h
    │               ├── loader/
    │               │   ├── assembly-arm.h
    │               │   ├── assembly-arm64.h
    │               │   ├── assembly-x86.h
    │               │   ├── assembly-x86_64.h
    │               │   ├── assembly.S
    │               │   ├── loader-info.awk
    │               │   ├── loader-info.d
    │               │   ├── loader.c
    │               │   ├── script.h
    │               │   ├── wrap_loader-m32.sh
    │               │   └── wrap_loader.sh
    │               ├── path/
    │               │   ├── binding.c
    │               │   ├── binding.h
    │               │   ├── canon.c
    │               │   ├── canon.h
    │               │   ├── f2fs-bug.c
    │               │   ├── f2fs-bug.h
    │               │   ├── glue.c
    │               │   ├── glue.h
    │               │   ├── path.c
    │               │   ├── path.h
    │               │   ├── proc.c
    │               │   ├── proc.h
    │               │   ├── temp.c
    │               │   └── temp.h
    │               ├── ptrace/
    │               │   ├── ptrace.c
    │               │   ├── ptrace.h
    │               │   ├── user.c
    │               │   ├── user.h
    │               │   ├── wait.c
    │               │   └── wait.h
    │               ├── syscall/
    │               │   ├── chain.c
    │               │   ├── chain.h
    │               │   ├── exit.c
    │               │   ├── heap.c
    │               │   ├── heap.h
    │               │   ├── rlimit.c
    │               │   ├── rlimit.h
    │               │   ├── seccomp.c
    │               │   ├── seccomp.h
    │               │   ├── socket.c
    │               │   ├── socket.h
    │               │   ├── syscall.c
    │               │   ├── syscall.h
    │               │   ├── sysnum.c
    │               │   ├── sysnum.h
    │               │   ├── sysnums-arm.h
    │               │   ├── sysnums-arm64.h
    │               │   ├── sysnums-i386.h
    │               │   ├── sysnums-sh4.h
    │               │   ├── sysnums-x32.h
    │               │   ├── sysnums-x86_64.h
    │               │   └── sysnums.list
    │               ├── talloc/
    │               │   ├── closefrom.c
    │               │   ├── config.h
    │               │   ├── replace.c
    │               │   ├── replace.h
    │               │   ├── win32_replace.h
    │               │   └── system/
    │               │       ├── README
    │               │       ├── capability.h
    │               │       ├── dir.h
    │               │       ├── filesys.h
    │               │       ├── glob.h
    │               │       ├── gssapi.h
    │               │       ├── iconv.h
    │               │       ├── kerberos.h
    │               │       ├── locale.h
    │               │       ├── network.h
    │               │       ├── passwd.h
    │               │       ├── python.h
    │               │       ├── readline.h
    │               │       ├── select.h
    │               │       ├── shmem.h
    │               │       ├── syslog.h
    │               │       ├── terminal.h
    │               │       ├── threads.h
    │               │       ├── time.h
    │               │       ├── wait.h
    │               │       └── wscript_configure
    │               └── tracee/
    │                   ├── abi.h
    │                   ├── event.c
    │                   ├── event.h
    │                   ├── mem.c
    │                   ├── mem.h
    │                   ├── reg.c
    │                   ├── reg.h
    │                   ├── seccomp.c
    │                   ├── seccomp.h
    │                   ├── statx.c
    │                   ├── statx.h
    │                   ├── tracee.c
    │                   └── tracee.h
    ├── scripts/
    │   └── update_json.py
    ├── terminal-emulator/
    │   ├── proguard-rules.pro
    │   └── src/
    │       ├── main/
    │       │   ├── AndroidManifest.xml
    │       │   ├── java/
    │       │   │   └── com/
    │       │   │       └── termux/
    │       │   │           └── terminal/
    │       │   │               ├── AndroidUtils.java
    │       │   │               ├── ByteQueue.java
    │       │   │               ├── ITermImage.java
    │       │   │               ├── JNI.java
    │       │   │               ├── KeyHandler.java
    │       │   │               ├── Logger.java
    │       │   │               ├── TerminalBitmap.java
    │       │   │               ├── TerminalBuffer.java
    │       │   │               ├── TerminalColors.java
    │       │   │               ├── TerminalColorScheme.java
    │       │   │               ├── TerminalOutput.java
    │       │   │               ├── TerminalRow.java
    │       │   │               ├── TerminalSession.java
    │       │   │               ├── TerminalSessionClient.java
    │       │   │               ├── TerminalSixel.java
    │       │   │               ├── TextStyle.java
    │       │   │               └── WcWidth.java
    │       │   └── jni/
    │       │       ├── Android.mk
    │       │       └── termux.c
    │       └── test/
    │           └── java/
    │               └── com/
    │                   └── termux/
    │                       └── terminal/
    │                           ├── ApcTest.java
    │                           ├── ByteQueueTest.java
    │                           ├── ControlSequenceIntroducerTest.java
    │                           ├── CursorAndScreenTest.java
    │                           ├── DecSetTest.java
    │                           ├── DeviceControlStringTest.java
    │                           ├── HistoryTest.java
    │                           ├── KeyHandlerTest.java
    │                           ├── OperatingSystemControlTest.java
    │                           ├── RectangularAreasTest.java
    │                           ├── ResizeTest.java
    │                           ├── ScreenBufferTest.java
    │                           ├── ScrollRegionTest.java
    │                           ├── TerminalRendererBufferTest.java
    │                           ├── TerminalRowTest.java
    │                           ├── TerminalTest.java
    │                           ├── TerminalTestCase.java
    │                           ├── TextStyleTest.java
    │                           ├── UnicodeInputTest.java
    │                           └── WcWidthTest.java
    ├── terminal-view/
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── com/
    │           │       └── termux/
    │           │           └── view/
    │           │               ├── BidiLayout.java
    │           │               ├── GestureAndScaleRecognizer.java
    │           │               ├── TerminalRenderer.java
    │           │               ├── TerminalViewClient.java
    │           │               ├── completion/
    │           │               │   ├── TerminalAutoCompletion.java
    │           │               │   ├── TerminalCompletionAdapter.java
    │           │               │   ├── TerminalCompletionIconDrawable.java
    │           │               │   ├── TerminalCompletionItem.java
    │           │               │   ├── TerminalCompletionItemKind.java
    │           │               │   └── providers/
    │           │               │       ├── CommandCompletionProvider.java
    │           │               │       ├── HistoryCompletionProvider.java
    │           │               │       └── TerminalCompletionProvider.java
    │           │               ├── support/
    │           │               │   └── PopupWindowCompatGingerbread.java
    │           │               └── textselection/
    │           │                   ├── CursorController.java
    │           │                   ├── TextSelectionCursorController.java
    │           │                   └── TextSelectionHandleView.java
    │           └── res/
    │               ├── drawable/
    │               │   ├── terminal_completion_popup_bg.xml
    │               │   ├── text_select_handle_left_material.xml
    │               │   └── text_select_handle_right_material.xml
    │               ├── layout/
    │               │   ├── terminal_completion_item.xml
    │               │   └── terminal_completion_popup.xml
    │               ├── values/
    │               │   └── strings.xml
    │               ├── values-ar/
    │               │   └── strings.xml
    │               ├── values-fa/
    │               │   └── strings.xml
    │               ├── values-hi/
    │               │   └── strings.xml
    │               ├── values-pt/
    │               │   └── strings.xml
    │               ├── values-ru/
    │               │   └── strings.xml
    │               └── values-zh/
    │                   └── strings.xml
    ├── thememanager/
    │   ├── consumer-rules.pro
    │   ├── proguard-rules.pro
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           └── java/
    │               └── ir/
    │                   └── theme/
    │                       ├── ActivityTheme.java
    │                       ├── EditorTheme.java
    │                       ├── GhostTheme.java
    │                       ├── GthDecoder.java
    │                       ├── M3Theme.java
    │                       ├── MaterialTheme.java
    │                       ├── ThemeBus.java
    │                       ├── ThemeManager.java
    │                       ├── ThemeMediaPath.java
    │                       ├── ThemePreviewBuilder.java
    │                       ├── WidgetTheme.java
    │                       ├── colorscheme/
    │                       │   └── GhostColorScheme.java
    │                       └── internal/
    │                           ├── ThemeConstKeys.java
    │                           ├── ThemeFileUtil.java
    │                           ├── ThemePrefsHelper.java
    │                           └── ThemeRefResolver.java
    ├── vectormaster/
    │   ├── build.gradle
    │   └── src/
    │       └── main/
    │           ├── AndroidManifest.xml
    │           ├── java/
    │           │   └── ir/
    │           │       └── hanzodev1375/
    │           │           └── filetreelib/
    │           │               └── vectormaster/
    │           │                   ├── DefaultValues.java
    │           │                   ├── VectorMasterDrawable.java
    │           │                   ├── VectorMasterView.java
    │           │                   ├── drawablexml/
    │           │                   │   ├── DrawableXmlLoader.java
    │           │                   │   └── XmlShapeParser.java
    │           │                   ├── enums/
    │           │                   │   └── TintMode.java
    │           │                   ├── models/
    │           │                   │   ├── ClipPathModel.java
    │           │                   │   ├── GroupModel.java
    │           │                   │   ├── PathModel.java
    │           │                   │   └── VectorModel.java
    │           │                   └── utilities/
    │           │                       ├── Utils.java
    │           │                       ├── legacyparser/
    │           │                       │   └── PathParser.java
    │           │                       └── parser/
    │           │                           ├── ParserHelper.java
    │           │                           └── PathParser.java
    │           └── res/
    │               └── values/
    │                   └── attrs.xml
    └── .github/
        ├── ISSUE_TEMPLATE/
        │   ├── bug_report.md
        │   └── feature_request.md
        └── workflows/
            ├── android-ci.yml
            └── android.yml
