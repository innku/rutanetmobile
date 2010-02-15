require 'rho/rhocontroller'

class ContactController < Rho::RhoController

  #GET /Contact
  def index
    @contacts = Contact.find(:all)
    render
  end

  # GET /Contact/{1}
  def show
    @contact = Contact.find(@params['id'])
    render :action => :show
  end

  # GET /Contact/new
  def new
    @contact = Contact.new
    render :action => :new
  end

  # GET /Contact/{1}/edit
  def edit
    @contact = Contact.find(@params['id'])
    render :action => :edit
  end

  # POST /Contact/create
  def create
    @contact = Contact.new(@params['contact'])
    @contact.save
    SyncEngine.dosync
    puts "ID DEL CONTACTO #{@contact.id}"
    WebView.navigate ( url_for :action => :show, :id => @contact.id )
  end

  # POST /Contact/{1}/update
  def update
    @contact = Contact.find(@params['id'])
    @contact.update_attributes(@params['contact'])
    redirect :action => :index
  end

  # POST /Contact/{1}/delete
  def delete
    @contact = Contact.find(@params['id'])
    @contact.destroy
    redirect :action => :index
  end
end
