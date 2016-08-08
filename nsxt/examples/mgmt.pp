nsxt_add_to_fabric { 'Add transport node':
  managers => 'https://172.16.0.245, https://172.16.0.249:443',
  username => 'admin',
  password => 'Qwer!1234',
#  ensure => absent,
  ensure => present,
#  ca_file => '/tmp/ca.crt'
}
