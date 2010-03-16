require 'rho/rhocontroller'

class MyfreightController < Rho::RhoController

  #GET /Myfreight
  def index
    SyncEngine.dosync
    @myfreights = Myfreight.find(:all)
    render
  end

  # GET /Myfreight/{1}
  def show
    @myfreight = Myfreight.find(@params['id'])
    render :action => :show
  end

end
