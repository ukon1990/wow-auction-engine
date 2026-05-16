/**
 * @typedef {object} ParsedArgs
 * @property {boolean} dryRun
 * @property {boolean} help
 * @property {number[] | null} professionIds
 * @property {number[] | null} skillTierIds
 * @property {string} resource
 * @property {number} sampleSize
 */

/**
 * @typedef {object} FixturePaths
 * @property {string} baseResources
 * @property {string} manifestFile
 */

/**
 * @typedef {object} RefreshContext
 * @property {import('../api/blizzard-client.mjs').ApiClient} apiClient
 * @property {ParsedArgs} args
 * @property {FixturePaths} paths
 * @property {object} [selectionConfig]
 */

/**
 * @typedef {object} WriteOperation
 * @property {string} filePath
 * @property {'write'} kind
 * @property {unknown} payload
 * @property {boolean} [raw]
 */

/**
 * @typedef {object} DeleteOperation
 * @property {string} filePath
 * @property {'delete'} kind
 */

/**
 * @typedef {object} RefreshPlan
 * @property {DeleteOperation[]} deletes
 * @property {WriteOperation[]} writes
 * @property {object} meta
 * @property {object} summary
 */

/**
 * @typedef {object} ResourceDefinition
 * @property {string} name
 * @property {(context: RefreshContext) => Promise<RefreshPlan>} buildPlan
 */

export {};
