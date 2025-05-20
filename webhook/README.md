# Webhook configuration and scripts

This folder documents the server settings needed to host JPhyloRef as a
web server using [Webhook](https://github.com/adnanh/webhook). This requires
the [`webhook` binary](https://github.com/adnanh/webhook/releases) to be
downloaded to the same folder as these scripts.

Webhook will set up a web server that listens for HTTP requests on the specified
port and will execute a program for each request that arrives. If you would like
your web server to run on that webserver, you can have webhook run JPhyloRef
directly with the "--errors-as-json" flag. If you need your web server to run
JPhyloRef on a cluster that uses [SLURM](https://slurm.schedmd.com/) as its
job manager, you can use the included `exec_jphyloref_webhook.sh` script to
start the job instead.

The files in this folder are:
* [start.sh](./start.sh) should be executed to start the server. It sets up the
[Access-Control-Allow-Origin](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Origin)
and
[Access-Control-Allow-Headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Headers)
HTTP headers to ensure that requests from other websites via
[CORS](https://en.wikipedia.org/wiki/Cross-origin_resource_sharing) are allowed. The `$PORT` environment variable should be set to the HTTP port to listen to.
* [hooks.json](./hooks.json) is the settings file for Webhook. It expects a
`jsonld` form value containing a JSON-LD document to be reasoned over. It uses
[X-Hub-Signature](https://www.npmjs.com/package/x-hub-signature) to ensure that
the browser knows a secret (which should be set as `$SECRET`) shared with the
reasoner.
* [exec_jphyloref.sh](exec_jphyloref.sh) is a helper script that executes JPhyloRef,
which will return a JSON document: either the resolution results or an error message.
Webhook will then send this back to the browser. As is usual for webhook, the filepath
of the input file is expected to be passed as the `$JSONLD_FILENAME` environmental variable.
* [exec_jphyloref_srun.sh](exec_jphyloref_srun.sh) is a helper script that
executes JPhyloRef using srun and prints the JSON document containing resolved
phyloreferences to standard output. It calls JPhyloRef with particular settings at three levels:
  * Some settings (number of tasks, number of CPUs per task, timeout) are sent to
    [`srun`](https://slurm.schedmd.com/srun.html), which starts JPhyloRef on a cluster.
  * Some settings (maximum memory, HTTP proxy settings) are sent to Java to set up
    the environment in which JPhyloRef can run.
  * Some settings (the name of the input file) are sent to JPhyloRef for processing.
