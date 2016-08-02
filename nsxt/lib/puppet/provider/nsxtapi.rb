# require ruby-rest-client, ruby-json
require 'rest-client'
require 'json'

class Puppet::Provider::Nsxtapi < Puppet::Provider

  def get_nsxt_api(api_url, username, password, timeout=10)
    retry_count = 3
    begin
      response = RestClient::Request.execute(method: :get, url: api_url, timeout: timeout, user: username, password: password)
      response_hash = JSON.parse(response.body)
      return response_hash
    rescue => error
      retry_count -= 1
      if retry_count > 0
        sleep 10
        retry
      else
        raise error.message
      end
    end
  end

end
