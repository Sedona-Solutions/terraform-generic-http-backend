package fr.sedona.terraform.storage.elasticsearch.service

import com.fasterxml.jackson.databind.ObjectMapper
import fr.sedona.terraform.http.model.TfLockInfo
import fr.sedona.terraform.http.model.TfState
import fr.sedona.terraform.storage.model.State
import io.vertx.core.json.JsonObject
import org.apache.http.util.EntityUtils
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.elasticsearch.client.Request
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestClient
import org.jboss.logging.Logger
import java.util.*
import javax.enterprise.context.ApplicationScoped
import kotlin.collections.ArrayList

/**
 * Service for State management in Elasticsearch using low level REST client.
 *
 * <p>
 *     Compared to High Level REST client, this service is version agnostic, being based on very simple and stable endpoints.
 * </p>
 */
@ApplicationScoped
class ElasticsearchStateService (
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = Logger.getLogger(ElasticsearchStateService::class.java)

    @ConfigProperty(name = "quarkus.elasticsearch.index.name", defaultValue = "state")
    lateinit var indexName: String;

    @ConfigProperty(name = "quarkus.elasticsearch.index.settings", defaultValue = "{\"settings\":{\"number_of_replicas\":\"1\", \"number_of_shards\":\"3\"}}")
    lateinit var indexSettings: String

    @ConfigProperty(name = "quarkus.elasticsearch.list.page-size", defaultValue = "500")
    lateinit var pageSize: String

    /**
     * see https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-index_.html#docs-index-api-response-body
     */
    private val SUCCESS_UPDATE_RESULTS = listOf("created","updated")

    fun listAll(): List<State> {
        logger.debug("Listing all states")

        val request = Request("GET", "/$indexName/_search?scroll=1m")
        request.setJsonEntity("{\"size\":$pageSize}")
        val response = restClient.performRequest(request)
        val responseBody = JsonObject(EntityUtils.toString(response.entity))
        val hitsObject = responseBody.getJsonObject("hits")
        val results = extractResultPage(hitsObject)

        val total = countAllStates()

        if(total > pageSize.toInt()){
            val scrollId = responseBody.getString("_scroll_id")
            loopOnScroll(scrollId, total, results)
        }

        logger.debug("Listed all states : total of ${results.size} results found")
        return results
    }

    fun update(stateToUpdate: State) {
        logger.debug("Updating state for project ${stateToUpdate.name}")

        val request = Request("PUT", "/$indexName/_doc/${stateToUpdate.name}")

        request.setJsonEntity(JsonObject.mapFrom(stateToUpdate).toString())

        val response = restClient.performRequest(request)
        val responseBody = EntityUtils.toString(response.entity)
        val updateResult = JsonObject(responseBody).getString("result");

        if(updateResult in SUCCESS_UPDATE_RESULTS) {
            logger.info("State for project ${stateToUpdate.name} updated successfully : result => ${updateResult}")
        } else {
            logger.info("State for project ${stateToUpdate.name} cannot be created nor updated : result => ${updateResult}")
        }
    }

    fun get(stateName: String): State {
        val request = Request("GET", "/$indexName/_doc/$stateName")

        try {
            val response = restClient.performRequest(request)
            val responseBody = EntityUtils.toString(response.entity)
            val json = JsonObject(responseBody)

            return json.getJsonObject("_source").mapTo<State>(State::class.java)
        } catch (e: ResponseException) {
            throw NoSuchElementException()
        }
    }

    fun delete(stateName: String) {
        val request = Request("DELETE", "/$indexName/_doc/$stateName")
        val response = restClient.performRequest(request)
        val responseBody = EntityUtils.toString(response.entity)
        val json = JsonObject(responseBody)
        if(!json.getBoolean("acknowledged")) {
            throw Exception("failed deletion")
        }
    }

    fun lock(project: String, stateToUpdate: State, lockInfo: TfLockInfo): State {
        logger.debug("Locking state for project $project")

        // Update the locking path
        val updatedLock = lockInfo.copy(
            path = project
        )

        // Update the existing state
        stateToUpdate.lastModified = Date()
        stateToUpdate.locked = true
        stateToUpdate.lockId = updatedLock.id!!
        stateToUpdate.tfVersion = updatedLock.version
        stateToUpdate.lockInfo = objectMapper.writeValueAsString(updatedLock)
        update(stateToUpdate)

        logger.info("State for project $project locked")
        return stateToUpdate
    }

    fun unlock(project: String, stateToUnlock: State): State {
        logger.debug("Unlocking state for project $project")
        stateToUnlock.locked = false
        stateToUnlock.lockId = null
        stateToUnlock.lockInfo = null
        update(stateToUnlock)

        logger.info("State for project $project unlocked")
        return stateToUnlock
    }

    fun searchByName(name: String): List<State> {
        return search("name", name)
    }

    private fun search(field: String, query: String): List<State> {
        val request = Request("GET", "/$indexName/_search")

        val queryJson = buildSearchQuery(field, query)

        request.setJsonEntity(queryJson?.encode());

        val response = restClient.performRequest(request)

        val responseBody = EntityUtils.toString(response.entity)
        val json = JsonObject(responseBody)
        val hits = json.getJsonObject("hits").getJsonArray("hits")

        val results: ArrayList<State> = ArrayList(hits.size())
        for (i in 0 until hits.size()) {
            val hit = hits.getJsonObject(i)
            val state: State = hit.getJsonObject("_source").mapTo<State>(State::class.java)
            results.add(state)
        }
        return results
    }

    private fun buildSearchQuery(field: String, query: String): JsonObject? {
        //construct a JSON query like {"query": {"match": {"<field>": "<query>"}}
        val termJson = JsonObject().put(field, query)
        val matchJson = JsonObject().put("match", termJson)
        val queryJson = JsonObject().put("query", matchJson)
        return queryJson
    }

    fun createAndLock(project: String, lockInfo: TfLockInfo): State {
        logger.debug("Creating and locking state for project $project")
        // Update the locking path
        val updatedLock = lockInfo.copy(
            path = project
        )

        // Create the default empty state
        val state = State()
        state.name = project
        state.lastModified = Date()
        state.locked = true
        state.lockId = updatedLock.id!!
        state.tfVersion = updatedLock.version
        state.lockInfo = objectMapper.writeValueAsString(updatedLock)
        val defaultState = TfState(
            version = 4,
            tfVersion = updatedLock.version,
            serial = 1,
            lineage = null,
            outputs = null,
            resources = null
        )
        state.state = objectMapper.writeValueAsString(defaultState)
        update(state)

        logger.info("State for project $project created and locked")
        return state
    }

    fun initializeIndexIfNeeded() {
        logger.info("Checking if index $indexName already exists")

        val request = Request("GET", "/$indexName/")

        try {
            restClient.performRequest(request)
        } catch (e: ResponseException) {
            createIndex()
        }
        logger.info("Index $indexName exists")
    }

    private fun createIndex() {
        val indexCreationRequest = Request("PUT", "/$indexName")
        if (!indexSettings.isNullOrEmpty()) {
            logger.info("Using settings $indexSettings to create Index $indexName")
            indexCreationRequest.setJsonEntity(indexSettings)
        }
        val indexCreationResponse = restClient.performRequest(indexCreationRequest)
        val indexCreationResponseBody = EntityUtils.toString(indexCreationResponse.entity)
        val acknowledged = JsonObject(indexCreationResponseBody).getBoolean("acknowledged");
        if (!acknowledged) {
            throw Exception("Cannot create $indexName index")
        }
        logger.info("Index $indexName created to store states")
    }


    /**
     * Count how many states are stored in the index. This use a endpoint which is the same
     * in both v6.X and v7.X as the "total" attribute of search response has been modified between these versions.
     */
    private fun countAllStates(): Int {
        val countRequest = Request("GET", "/$indexName/_count")
        val countResponse = restClient.performRequest(countRequest)
        return JsonObject(EntityUtils.toString(countResponse.entity)).getInteger("count")
    }

    /**
     * Loop on scrolled search request to retrieve all results (working even for large numbers of results)
     */
    private fun loopOnScroll(scrollId: String, total: Int, resultList: ArrayList<State>) {
        var pageIndex = 0;
        while(total > (pageIndex+1)*pageSize.toInt()) {
            pageIndex++
            logger.debug("Getting next page of states list results (index=$pageIndex / size=$pageSize)")
            val scrollRequest = Request("POST", "/_search/scroll?scroll=1m&scroll_id=$scrollId")
            val scrollResponse = restClient.performRequest(scrollRequest)
            val scrollResponseBody = JsonObject(EntityUtils.toString(scrollResponse.entity))
            val scrollHitsObject = scrollResponseBody.getJsonObject("hits")
            val elements = extractResultPage(scrollHitsObject)
            logger.debug("Page of states list results (index=$pageIndex) => ${elements.size} results found")
            resultList.addAll(elements)
        }
    }

    /**
     * Extract state objects from hits JSON Array
     */
    private fun extractResultPage(hitsObject: JsonObject): ArrayList<State> {
        val page = hitsObject.getJsonArray("hits")
        val resultList = ArrayList<State>()
        for (i in 0 until page.size()) {
            val item = page.getJsonObject(i)
            val resultItem = item.getJsonObject("_source").mapTo<State>(State::class.java)
            resultList.add(resultItem)
        }
        return resultList
    }
}
