'use strict'

const aggregateContent = require('@antora/content-aggregator')
const buildNavigation = require('@antora/navigation-builder')
const buildPlaybook = require('@antora/playbook-builder')
const classifyContent = require('@antora/content-classifier')
const convertDocuments = require('@antora/document-converter')
const createPageComposer = require('@antora/page-composer')
const loadUi = require('@antora/ui-loader')
const mapSite = require('@antora/site-mapper')
const produceRedirects = require('@antora/redirect-producer')
const publishSite = require('@antora/site-publisher')
const { resolveAsciiDocConfig } = require('@antora/asciidoc-loader')

async function generateSite (args, env) {
  const playbook = buildPlaybook(args, env)
  let currentPlaybook
  if (env.TRAINING_MODULE) {
    // build only one module to make the preview faster
    const sources = playbook.content.sources.map((source) => {
      if (source.url === '.') {
        return {
          ...source, startPaths: [
            `modules/home`,
            `modules/${env.TRAINING_MODULE}`
          ]
        }
      }
      return source
    })
    currentPlaybook = {...playbook}
    currentPlaybook.content = {...playbook.content}
    currentPlaybook.content.sources = sources
  } else {
    currentPlaybook = playbook
  }
  const asciidocConfig = resolveAsciiDocConfig(currentPlaybook)
  const [contentCatalog, uiCatalog] = await Promise.all([
    aggregateContent(currentPlaybook).then((contentAggregate) => classifyContent(currentPlaybook, contentAggregate, asciidocConfig)),
    loadUi(currentPlaybook),
  ])
  const pages = convertDocuments(contentCatalog, asciidocConfig)
  const navigationCatalog = buildNavigation(contentCatalog, asciidocConfig)
  const composePage = createPageComposer(currentPlaybook, contentCatalog, uiCatalog, env)
  pages.forEach((page) => composePage(page, contentCatalog, navigationCatalog))
  const siteFiles = [...mapSite(currentPlaybook, pages), ...produceRedirects(currentPlaybook, contentCatalog)]
  if (currentPlaybook.site.url) siteFiles.push(composePage(create404Page()))
  const siteCatalog = { getFiles: () => siteFiles }
  publishSite(currentPlaybook, [contentCatalog, uiCatalog, siteCatalog])
  return contentCatalog
}

function create404Page () {
  return {
    title: 'Page Not Found',
    mediaType: 'text/html',
    src: { stem: '404' },
    out: { path: '404.html' },
    pub: { url: '/404.html', rootPath: '' },
  }
}

module.exports = generateSite
