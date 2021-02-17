const fs = require('fs-extra')
const path = require('path')
const util = require('util')

const debug = require('debug')
const copyImagesDebug = debug('copy-images')
const convertDebug = debug('convert-jupyter')
const glob = util.promisify(require('glob'))
const asciidoctor = require('@asciidoctor/core')()
const JupyterConverter = require('asciidoctor-jupyter')

asciidoctor.ConverterFactory.register(JupyterConverter, ['jupyter'])


const baseDir = path.join(__dirname, '..', '..')

;(async() => {
  try {
    console.log('copying images to build...')
    const imagesDirs = await glob('browser-guides/*/images', { cwd: baseDir })
    for (const imagesDir of imagesDirs) {
      const destinationDir = path.normalize(path.join(baseDir, imagesDir, '..', 'build', 'notebook', 'images'))
      copyImagesDebug(`copy ${imagesDir} to ${path.relative(baseDir, destinationDir)}`)
      await fs.copy(imagesDir, destinationDir)
    }
    console.log('converting AsciiDoc files to Jupyter notebooks...')
    const files = await glob('browser-guides/**/docs/*.adoc', { cwd: baseDir })
    for (const file of files) {
      const destinationDir = path.normalize(path.join(baseDir, file, '..', '..', 'build', 'notebook'))
      convertDebug(`convert ${file} to ${path.relative(baseDir, path.join(destinationDir, path.basename(file, '.adoc') + '.ipynb'))}`)
      asciidoctor.convertFile(path.join(baseDir, file), {
        safe: 'unsafe',
        backend: 'jupyter',
        to_dir: destinationDir,
        mkdirs: true,
        attributes: {
          imagesdir: 'images' 
        }
      })
    }
  } catch (e) {
    console.error('Something went wrong!', e)
  }
})()
