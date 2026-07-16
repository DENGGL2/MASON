package com.denggl2.mason.integration

data class McpServiceCatalogEntry(
    val id: String,
    val name: String,
    val purpose: String,
    val endpoint: String,
    val supportsToken: Boolean,
    val supportsOAuth: Boolean,
    val scopes: List<String> = emptyList(),
)

object McpServiceCatalog {
    val github = McpServiceCatalogEntry(
        id = "github",
        name = "GitHub",
        purpose = "查看仓库、Issue 和 Pull Request，并执行研发协作任务",
        endpoint = "https://api.githubcopilot.com/mcp/",
        supportsToken = true,
        supportsOAuth = true,
        scopes = listOf("repo", "read:org", "read:user"),
    )

    val entries = listOf(github)
}
