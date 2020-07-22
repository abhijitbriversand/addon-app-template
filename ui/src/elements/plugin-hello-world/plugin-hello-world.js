import { PolymerElement, html } from '@polymer/polymer/polymer-element.js';

// ui-platform-utils
import { ObjectUtils } from 'ui-platform-utils/lib/common/ObjectUtils.js';

// ui-platform-elements
import 'ui-platform-elements/lib/elements/pebble-textbox/pebble-textbox.js';

// ui-platform-dataaccess
import { ConfigurationManager } from 'ui-platform-dataaccess/lib/managers/ConfigurationManager.js';

class PluginHelloWorld extends PolymerElement {
    static get is() {
        return 'plugin-hello-world';
    }

    static get template() {
        return html` <div>
                [[label]]
            </div>

            <br />

            <div>
                [[message]]
            </div>

            <br /><br />

            <pebble-textbox value="And...hello from pebble-textbox"> </pebble-textbox>`;
    }

    static get properties() {
        return {
            label: {
                type: String,
                value: 'Hello world!!'
            },
            message: {
                type: String,
                value: ''
            }
        };
    }

    async connectedCallback() {
        super.connectedCallback();

        let configResponse = await ConfigurationManager.getConfig('plugin-hello-world');

        if (
            ObjectUtils.isValidObjectPath(configResponse, 'response.status') &&
            configResponse.response.status == 'success'
        ) {
            this._handleConfigGetSuccess(configResponse);
        } else {
            this._handleConfigGetError(configResponse);
        }
    }

    _handleConfigGetSuccess(configResponse) {
        let res = configResponse.response.content;
        let compConfig = {};

        if (ObjectUtils.isValidObjectPath(res, 'configObjects.0.data.contexts.0.jsonData')) {
            compConfig = res.configObjects[0].data.contexts[0].jsonData;

            if (ObjectUtils.isEmpty(compConfig)) {
                console.error('UI config is empty', configResponse);
            } else {
                if (compConfig.config) {
                    let config = compConfig.config;

                    if (config.properties) {
                        for (let propKey in config.properties) {
                            this.set(propKey, config.properties[propKey]);
                        }
                    }
                }
            }
        }
    }

    _handleConfigGetError(configResponse) {
        console.error('UI config get failed with error', configResponse);
    }
}

customElements.define(PluginHelloWorld.is, PluginHelloWorld);
