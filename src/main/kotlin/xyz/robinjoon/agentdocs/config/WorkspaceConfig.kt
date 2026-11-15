package xyz.robinjoon.agentdocs.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import tools.jackson.databind.json.JsonMapper
import xyz.robinjoon.agentdocs.access.application.port.input.CallerUseCase
import xyz.robinjoon.agentdocs.access.application.port.input.WebSessionUseCase
import xyz.robinjoon.agentdocs.access.application.service.CurrentCallerService
import xyz.robinjoon.agentdocs.application.port.input.CurrentCallerUseCase
import xyz.robinjoon.agentdocs.application.port.output.ActivityCatalog
import xyz.robinjoon.agentdocs.application.port.output.ActivityReporter
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.scheduling.PendingEditScheduler
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.startup.FolderBackfill
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.DocumentEventCaller
import xyz.robinjoon.agentdocs.workspace.adapter.inbound.web.EditStatisticsRecorder
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.archive.ZipExportArchive
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.crypto.SecureRandomUploadSecretSource
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.http.HttpRemoteImageSource
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.image.SniffingImageVerifier
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.image.SvgSanitizer
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage.FileSystemAssetStore
import xyz.robinjoon.agentdocs.workspace.adapter.outbound.storage.S3AssetStore
import xyz.robinjoon.agentdocs.workspace.application.port.input.ActivityUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.AssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentAssetUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentEventsUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentQueryUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentTransferUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.DocumentUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.EditSessionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ExportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.FolderImportUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.InboxUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.NavigationUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SearchUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SpaceUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.SuggestionUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.ThreadUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.input.UploadGrantUseCase
import xyz.robinjoon.agentdocs.workspace.application.port.output.AssetStore
import xyz.robinjoon.agentdocs.workspace.application.service.ActivityService
import xyz.robinjoon.agentdocs.workspace.application.service.AssetService
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentAssetService
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentEventsService
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentQueryService
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentService
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentTransferAssets
import xyz.robinjoon.agentdocs.workspace.application.service.DocumentTransferService
import xyz.robinjoon.agentdocs.workspace.application.service.EditSessionService
import xyz.robinjoon.agentdocs.workspace.application.service.ExportService
import xyz.robinjoon.agentdocs.workspace.application.service.FolderImportService
import xyz.robinjoon.agentdocs.workspace.application.service.InboxService
import xyz.robinjoon.agentdocs.workspace.application.service.NavigationService
import xyz.robinjoon.agentdocs.workspace.application.service.SearchService
import xyz.robinjoon.agentdocs.workspace.application.service.SpaceService
import xyz.robinjoon.agentdocs.workspace.application.service.SuggestionService
import xyz.robinjoon.agentdocs.workspace.application.service.ThreadService
import xyz.robinjoon.agentdocs.workspace.application.service.UploadGrantService
import xyz.robinjoon.agentdocs.workspace.application.service.WriteJournal
import java.net.URI
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

@Configuration(proxyBeanMethods = false)
class WorkspaceConfig {
    @Bean
    fun workspacePersistence(): WorkspacePersistence = WorkspacePersistence()

    // Files go to the homelab object storage with agent-docs.assets.store=s3, and to a local directory otherwise.
    @Bean
    fun assetPorts(properties: AgentDocsProperties): AssetPorts =
        AssetPorts(assetStoreOf(properties.assets), SniffingImageVerifier(SvgSanitizer()), HttpRemoteImageSource())

    @Bean
    fun writeJournal(
        activity: ActivityReporter,
        persistence: WorkspacePersistence,
        clock: Clock,
    ): WriteJournal = WriteJournal(activity, persistence.operations, clock)

    @Bean
    fun spaceUseCase(
        persistence: WorkspacePersistence,
        journal: WriteJournal,
    ): SpaceUseCase = SpaceService(persistence.spaces, persistence.statistics, persistence.spaceGuides, journal)

    @Bean
    fun documentUseCase(
        persistence: WorkspacePersistence,
        journal: WriteJournal,
    ): DocumentUseCase = DocumentService(persistence.documents, persistence.revisions, persistence.spaces, persistence.threads, journal)

    // Folders from before documents had parent documents become documents in the owner's name on each start.
    @Bean
    fun folderBackfill(
        documents: DocumentUseCase,
        properties: AgentDocsProperties,
    ): FolderBackfill = FolderBackfill(documents, properties.owner)

    @Bean
    fun pendingEditScheduler(documents: DocumentUseCase): PendingEditScheduler = PendingEditScheduler(documents)

    @Bean
    fun documentQueryUseCase(persistence: WorkspacePersistence): DocumentQueryUseCase =
        DocumentQueryService(
            persistence.documents,
            persistence.revisions,
            persistence.spaces,
            persistence.threads,
        )

    @Bean
    fun threadUseCase(
        persistence: WorkspacePersistence,
        journal: WriteJournal,
    ): ThreadUseCase =
        ThreadService(persistence.threads, persistence.documents, persistence.spaces, persistence.inbox, persistence.revisions, journal)

    @Bean
    fun assetUseCase(
        persistence: WorkspacePersistence,
        ports: AssetPorts,
        journal: WriteJournal,
    ): AssetUseCase = AssetService(persistence.spaces, persistence.assets, ports.store, ports.verifier, ports.remote, journal)

    @Bean
    fun uploadGrantUseCase(
        persistence: WorkspacePersistence,
        assets: AssetUseCase,
        clock: Clock,
    ): UploadGrantUseCase =
        UploadGrantService(persistence.spaces, persistence.uploadGrants, SecureRandomUploadSecretSource(), assets, clock)

    @Bean
    fun searchUseCase(persistence: WorkspacePersistence): SearchUseCase =
        SearchService(persistence.search, persistence.documents, persistence.spaces)

    @Bean
    fun inboxUseCase(
        persistence: WorkspacePersistence,
        clock: Clock,
    ): InboxUseCase = InboxService(persistence.inbox, persistence.documents, persistence.spaces, clock)

    @Bean
    fun activityUseCase(
        catalog: ActivityCatalog,
        persistence: WorkspacePersistence,
    ): ActivityUseCase = ActivityService(catalog, persistence.spaces, persistence.documents)

    @Bean
    fun navigationUseCase(
        persistence: WorkspacePersistence,
        clock: Clock,
    ): NavigationUseCase =
        NavigationService(persistence.personalDocuments, persistence.navigation, persistence.documents, persistence.spaces, clock)

    @Bean
    fun editSessionUseCase(
        persistence: WorkspacePersistence,
        clock: Clock,
    ): EditSessionUseCase = EditSessionService(persistence.editSessions, persistence.documents, clock)

    @Bean
    fun editStatisticsRecorder(sessions: EditSessionUseCase): EditStatisticsRecorder = EditStatisticsRecorder(sessions)

    @Bean
    fun suggestionUseCase(
        persistence: WorkspacePersistence,
        journal: WriteJournal,
    ): SuggestionUseCase = SuggestionService(persistence.threads, persistence.documents, journal, persistence.inbox)

    @Bean
    fun folderImportUseCase(
        assets: AssetUseCase,
        documents: DocumentUseCase,
    ): FolderImportUseCase = FolderImportService(assets, documents)

    @Bean
    fun exportUseCase(
        persistence: WorkspacePersistence,
        json: JsonMapper,
        ports: AssetPorts,
    ): ExportUseCase = ExportService(persistence.exports, ZipExportArchive(json, ports.store))

    @Bean
    fun documentEventsUseCase(persistence: WorkspacePersistence): DocumentEventsUseCase = DocumentEventsService(persistence.documentEvents)

    @Bean
    fun currentCallerUseCase(
        callers: CallerUseCase,
        sessions: WebSessionUseCase,
    ): CurrentCallerUseCase = CurrentCallerService(callers, sessions)

    @Bean
    fun documentEventCaller(callers: CurrentCallerUseCase): DocumentEventCaller = DocumentEventCaller(callers)

    @Bean(destroyMethod = "shutdownNow")
    fun documentEventExecutor(): ScheduledExecutorService = ScheduledThreadPoolExecutor(2).apply { removeOnCancelPolicy = true }

    @Bean
    fun documentTransferUseCase(
        persistence: WorkspacePersistence,
        journal: WriteJournal,
    ): DocumentTransferUseCase =
        DocumentTransferService(
            persistence.documents,
            persistence.spaces,
            persistence.threads,
            persistence.inbox,
            DocumentTransferAssets(persistence.assets, persistence.documentAssets),
            journal,
        )

    @Bean
    fun documentAssetUseCase(
        persistence: WorkspacePersistence,
        ports: AssetPorts,
    ): DocumentAssetUseCase = DocumentAssetService(persistence.documents, persistence.documentAssets, persistence.assets, ports.store)

    private fun assetStoreOf(assets: AssetProperties): AssetStore {
        if (assets.store != "s3") return FileSystemAssetStore(Path.of(assets.directory))
        val s3 = assets.s3
        require(s3.bucket.isNotBlank() && s3.accessKey.isNotBlank() && s3.secretKey.isNotBlank()) {
            "agent-docs.assets.s3 needs bucket, access-key and secret-key when the store is s3"
        }
        val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey, s3.secretKey))
        val builder = S3Client.builder().region(Region.of(s3.region)).credentialsProvider(credentials)
        // S3-compatible servers such as the homelab's MinIO-based gateway predate the SDK's default trailing checksums,
        // so checksums are sent only where the API requires them.
        if (s3.endpoint.isNotBlank()) {
            builder
                .endpointOverride(URI.create(s3.endpoint))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        }
        return S3AssetStore(builder.build(), s3.bucket, s3.prefix)
    }
}
