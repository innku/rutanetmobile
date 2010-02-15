require 'rho/rhoapplication'

class AppApplication < Rho::RhoApplication
  def initialize
    @tabs = [{ :label => "Buscar", :action => '/app/Freight/new', :icon => "/public/images/search.png", :reload => true }, 
             { :label => "Cargas", :action => '/app/Freight/index', :icon => "/public/images/truck.png", :reload => true },
             { :label => "Contactos", :action => '/app/Contact/index', :icon => "/public/images/truck.png", :reload => true },
             { :label => "Configuracion", :action => '/app/Settings', :icon => "/public/images/settings.png", :reload => true }]
    super
  end
end
